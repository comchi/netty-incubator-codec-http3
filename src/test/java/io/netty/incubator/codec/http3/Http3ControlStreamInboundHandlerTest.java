/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.http3;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.netty.incubator.codec.http3.Http3TestUtils.assertException;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameReleased;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameSame;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

@RunWith(Parameterized.class)
public class Http3ControlStreamInboundHandlerTest extends
        AbstractHttp3FrameTypeValidationHandlerTest<Http3ControlStreamFrame> {

    private final boolean server;
    private final boolean forwardControlFrames;
    private QpackEncoder qpackEncoder;
    private Http3ControlStreamOutboundHandler remoteControlStreamHandler;

    public Http3ControlStreamInboundHandlerTest(boolean server, boolean forwardControlFrames) {
        super(true, QuicStreamType.UNIDIRECTIONAL);
        this.server = server;
        this.forwardControlFrames = forwardControlFrames;
    }

    @Parameterized.Parameters(name = "{index}: server = {0}, forwardControlFrames = {1}")
    public static Collection<Object[]> data() {
        List<Object[]> config = new ArrayList<>();
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                config.add(new Object[] { a == 0, b == 0 });
            }
        }
        return config;
    }

    @Before
    @Override
    public void setUp() {
        super.setUp();
        qpackEncoder = new QpackEncoder();
        remoteControlStreamHandler = new Http3ControlStreamOutboundHandler(server, new DefaultHttp3SettingsFrame(),
                new ChannelInboundHandlerAdapter());
    }

    @Override
    protected void afterSettingsFrameRead(Http3SettingsFrame settingsFrame) {
        if (!qpackAttributes.dynamicTableDisabled()) {
            // settings frame initialize QPACK streams
            readAndReleaseStreamHeader(qPACKEncoderStream());
            readAndReleaseStreamHeader(qPACKDecoderStream());
        }
    }

    @Override
    protected ChannelHandler newHandler() {
        return new Http3ControlStreamInboundHandler(true, new ChannelInboundHandlerAdapter(), qpackEncoder,
                remoteControlStreamHandler);
    }

    @Override
    protected List<Http3ControlStreamFrame> newValidFrames() {
        return Arrays.asList(new DefaultHttp3SettingsFrame(), new DefaultHttp3GoAwayFrame(0),
                new DefaultHttp3MaxPushIdFrame(0), new DefaultHttp3CancelPushFrame(0));
    }

    @Override
    protected List<Http3Frame> newInvalidFrames() {
        return Arrays.asList(Http3TestUtils.newHttp3RequestStreamFrame(), Http3TestUtils.newHttp3PushStreamFrame());
    }

    @Test
    public void testInvalidFirstFrameHttp3GoAwayFrame() throws Exception {
        testInvalidFirstFrame(new DefaultHttp3GoAwayFrame(0));
    }

    @Test
    public void testInvalidFirstFrameHttp3MaxPushIdFrame() throws Exception {
        testInvalidFirstFrame(new DefaultHttp3MaxPushIdFrame(0));
    }

    @Test
    public void testInvalidFirstFrameHttp3CancelPushFrame() throws Exception {
        testInvalidFirstFrame(new DefaultHttp3CancelPushFrame(0));
    }

    @Test
    public void testInvalidFirstFrameNonControlFrame() throws Exception {
        testInvalidFirstFrame(() -> 9999);
    }

    private void testInvalidFirstFrame(Http3Frame frame) throws Exception {
        final EmbeddedQuicStreamChannel channel = newStream(QuicStreamType.BIDIRECTIONAL,
                        new Http3ControlStreamInboundHandler(server,
                                forwardControlFrames ? new ChannelInboundHandlerAdapter() : null,
                                qpackEncoder, remoteControlStreamHandler));

        writeInvalidFrame(Http3ErrorCode.H3_MISSING_SETTINGS, channel, frame);
        verifyClose(Http3ErrorCode.H3_MISSING_SETTINGS, parent);

        assertFalse(channel.finish());
    }

    @Test
    public void testValidGoAwayFrame() throws Exception {
        EmbeddedChannel channel = newStream();
        writeValidFrame(channel, new DefaultHttp3GoAwayFrame(0));
        writeValidFrame(channel, new DefaultHttp3GoAwayFrame(0));
        assertFalse(channel.finish());
    }

    @Test
    public void testSecondGoAwayFrameFailsWithHigherId() throws Exception {
        EmbeddedChannel channel = newStream();
        writeValidFrame(channel, new DefaultHttp3GoAwayFrame(0));
        writeInvalidFrame(Http3ErrorCode.H3_ID_ERROR, channel, new DefaultHttp3GoAwayFrame(4));
        verifyClose(Http3ErrorCode.H3_ID_ERROR, parent);
        assertFalse(channel.finish());
    }

    @Test
    public void testGoAwayFrameIdNonRequestStream() throws Exception {
        EmbeddedChannel channel = newStream();
        if (server) {
            writeValidFrame(channel, new DefaultHttp3GoAwayFrame(3));
        } else {
            writeInvalidFrame(Http3ErrorCode.H3_FRAME_UNEXPECTED, channel, new DefaultHttp3GoAwayFrame(3));
            verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testHttp3MaxPushIdFrames() throws Exception {
        EmbeddedChannel channel = newStream();
        if (server) {
            writeValidFrame(channel, new DefaultHttp3MaxPushIdFrame(0));
            writeValidFrame(channel, new DefaultHttp3MaxPushIdFrame(4));
        } else {
            writeInvalidFrame(Http3ErrorCode.H3_FRAME_UNEXPECTED, channel, new DefaultHttp3MaxPushIdFrame(4));
            verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testSecondHttp3MaxPushIdFrameFailsWithSmallerId() throws Exception {
        assumeThat(server, is(true));
        EmbeddedChannel channel = newStream();
        writeValidFrame(channel, new DefaultHttp3MaxPushIdFrame(4));
        writeInvalidFrame(Http3ErrorCode.H3_ID_ERROR, channel, new DefaultHttp3MaxPushIdFrame(0));
        verifyClose(Http3ErrorCode.H3_ID_ERROR, parent);
        assertFalse(channel.finish());
    }

    private EmbeddedQuicStreamChannel newStream() throws Exception {
        EmbeddedQuicStreamChannel channel = newStream(QuicStreamType.UNIDIRECTIONAL,
                        new Http3ControlStreamInboundHandler(server,
                                forwardControlFrames ? new ChannelInboundHandlerAdapter() : null,
                                qpackEncoder, remoteControlStreamHandler));

        // We always need to start with a settings frame.
        Http3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();
        assertEquals(forwardControlFrames, channel.writeInbound(settingsFrame));
        if (forwardControlFrames) {
            assertFrameSame(settingsFrame, channel.readInbound());
        } else {
            assertFrameReleased(settingsFrame);
        }
        Object streamType = qPACKEncoderStream().readOutbound();
        assertNotNull(streamType);
        ReferenceCountUtil.release(streamType);

        streamType = qPACKDecoderStream().readOutbound();
        assertNotNull(streamType);
        ReferenceCountUtil.release(streamType);
        return channel;
    }

    private void writeValidFrame(EmbeddedChannel channel, Http3ControlStreamFrame controlStreamFrame) {
        assertEquals(forwardControlFrames, channel.writeInbound(controlStreamFrame));
        if (forwardControlFrames) {
            assertFrameSame(controlStreamFrame, channel.readInbound());
        } else {
            assertFrameReleased(controlStreamFrame);
        }
    }

    private void writeInvalidFrame(Http3ErrorCode expectedCode, EmbeddedChannel channel,
                                   Http3Frame frame) {
        if (forwardControlFrames) {
            try {
                channel.writeInbound(frame);
                fail();
            } catch (Exception e) {
                assertException(expectedCode, e);
            }
        } else {
            assertFalse(channel.writeInbound(frame));
        }
        assertFrameReleased(frame);
    }

    @Test
    public void testSecondSettingsFrameFails() throws Exception {
        EmbeddedChannel channel = newStream();
        writeInvalidFrame(Http3ErrorCode.H3_FRAME_UNEXPECTED, channel, new DefaultHttp3SettingsFrame());
        verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        assertFalse(channel.finish());
    }

    @Test
    public void testControlStreamClosed() throws Exception {
        EmbeddedQuicStreamChannel channel = newStream();
        channel.writeInboundFin();
        verifyClose(Http3ErrorCode.H3_CLOSED_CRITICAL_STREAM, parent);
        assertFalse(channel.finish());
    }

    @Override
    protected Http3ErrorCode inboundErrorCodeInvalid() {
        return Http3ErrorCode.H3_MISSING_SETTINGS;
    }
}
