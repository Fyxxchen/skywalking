/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.ai.cloud.io.netty.handler.codec.serialization;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

import com.ai.cloud.io.netty.buffer.ByteBuf;
import com.ai.cloud.io.netty.buffer.ByteBufOutputStream;
import com.ai.cloud.io.netty.channel.ChannelHandlerContext;
import com.ai.cloud.io.netty.handler.codec.MessageToByteEncoder;
import com.ai.cloud.io.netty.util.Attribute;
import com.ai.cloud.io.netty.util.AttributeKey;

/**
 * An encoder which serializes a Java object into a {@link ByteBuf}
 * (interoperability version).
 * <p>
 * This encoder is interoperable with the standard Java object streams such as
 * {@link ObjectInputStream} and {@link ObjectOutputStream}.
 */
public class CompatibleObjectEncoder extends MessageToByteEncoder<Serializable> {

    private static final AttributeKey<ObjectOutputStream> OOS =
            AttributeKey.valueOf(CompatibleObjectEncoder.class.getName() + ".OOS");

    private final int resetInterval;
    private int writtenObjects;

    /**
     * Creates a new instance with the reset interval of {@code 16}.
     */
    public CompatibleObjectEncoder() {
        this(16); // Reset at every sixteen writes
    }

    /**
     * Creates a new instance.
     *
     * @param resetInterval
     *        the number of objects between {@link ObjectOutputStream#reset()}.
     *        {@code 0} will disable resetting the stream, but the remote
     *        peer will be at the risk of getting {@link OutOfMemoryError} in
     *        the long term.
     */
    public CompatibleObjectEncoder(int resetInterval) {
        if (resetInterval < 0) {
            throw new IllegalArgumentException(
                    "resetInterval: " + resetInterval);
        }
        this.resetInterval = resetInterval;
    }

    /**
     * Creates a new {@link ObjectOutputStream} which wraps the specified
     * {@link OutputStream}.  Override this method to use a subclass of the
     * {@link ObjectOutputStream}.
     */
    protected ObjectOutputStream newObjectOutputStream(OutputStream out) throws Exception {
        return new ObjectOutputStream(out);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Serializable msg, ByteBuf out) throws Exception {
        Attribute<ObjectOutputStream> oosAttr = ctx.attr(OOS);
        ObjectOutputStream oos = oosAttr.get();
        if (oos == null) {
            oos = newObjectOutputStream(new ByteBufOutputStream(out));
            ObjectOutputStream newOos = oosAttr.setIfAbsent(oos);
            if (newOos != null) {
                oos = newOos;
            }
        }

        synchronized (oos) {
            if (resetInterval != 0) {
                // Resetting will prevent OOM on the receiving side.
                writtenObjects ++;
                if (writtenObjects % resetInterval == 0) {
                    oos.reset();
                }
            }

            oos.writeObject(msg);
            oos.flush();
        }
    }
}
