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

import io.netty.buffer.ByteBuf;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

final class Http3TestUtils {

    private Http3TestUtils() { }

    static void assertException(Http3ErrorCode code, Throwable e) {
        MatcherAssert.assertThat(e, CoreMatchers.instanceOf(Http3Exception.class));
        Http3Exception exception = (Http3Exception) e;
        assertEquals(code, exception.errorCode());
    }

    static void verifyClose(int times, Http3ErrorCode expectedCode, EmbeddedQuicChannel channel) {
        assertEquals("Close not invoked with expected times with error code: " + expectedCode.code, times,
                channel.closeErrorCodes().stream().filter(integer -> integer == expectedCode.code).count());
    }

    static void verifyClose(Http3ErrorCode expectedCode, EmbeddedQuicChannel channel) {
        verifyClose(1, expectedCode, channel);
    }

    static void assertBufferEquals(ByteBuf expected, ByteBuf actual) {
        try {
            assertEquals(expected, actual);
        } finally {
            ReferenceCountUtil.release(expected);
            ReferenceCountUtil.release(actual);
        }
    }

    static void assertFrameEquals(Http3Frame expected, Http3Frame actual) {
        try {
            assertEquals(expected, actual);
        } finally {
            ReferenceCountUtil.release(expected);
            ReferenceCountUtil.release(actual);
        }
    }

    static void assertFrameSame(Http3Frame expected, Http3Frame actual) {
        try {
            assertSame(expected, actual);
        } finally {
            // as both frames are the same we only want to release once.
            ReferenceCountUtil.release(actual);
        }
    }

    static void assertFrameReleased(Http3Frame frame) {
        if (frame instanceof ReferenceCounted) {
            assertEquals(0, ((ReferenceCounted) frame).refCnt());
        }
    }

    static Http3Frame newHttp3Frame() {
        return ()  -> 0;
    }

    static Http3PushStreamFrame newHttp3PushStreamFrame() {
        return ()  -> 0;
    }

    static Http3RequestStreamFrame newHttp3RequestStreamFrame() {
        return ()  -> 0;
    }

    static Http3ControlStreamFrame newHttp3ControlStreamFrame() {
        return ()  -> 0;
    }
}
