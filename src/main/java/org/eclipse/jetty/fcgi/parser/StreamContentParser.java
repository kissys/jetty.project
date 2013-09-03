//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.fcgi.parser;

import java.nio.ByteBuffer;

import org.eclipse.jetty.fcgi.FCGI;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class StreamContentParser extends ContentParser
{
    protected static final Logger logger = Log.getLogger(StreamContentParser.class);

    private final FCGI.StreamType streamType;
    private final Parser.Listener listener;
    private State state = State.LENGTH;
    private int contentLength;

    public StreamContentParser(HeaderParser headerParser, FCGI.StreamType streamType, Parser.Listener listener)
    {
        super(headerParser);
        this.streamType = streamType;
        this.listener = listener;
    }

    @Override
    public boolean parse(ByteBuffer buffer)
    {
        while (buffer.hasRemaining())
        {
            switch (state)
            {
                case LENGTH:
                {
                    contentLength = getContentLength();
                    state = State.CONTENT;
                    break;
                }
                case CONTENT:
                {
                    int length = Math.min(contentLength, buffer.remaining());
                    int limit = buffer.limit();
                    buffer.limit(buffer.position() + length);
                    ByteBuffer slice = buffer.slice();
                    onContent(slice);
                    buffer.position(buffer.limit());
                    buffer.limit(limit);
                    contentLength -= length;
                    if (contentLength > 0)
                        break;
                    state = State.LENGTH;
                    return true;
                }
                default:
                {
                    throw new IllegalStateException();
                }
            }
        }
        return false;
    }

    @Override
    public void noContent()
    {
        if (streamType == FCGI.StreamType.STD_IN)
            onEnd();
    }

    protected void onContent(ByteBuffer buffer)
    {
        try
        {
            listener.onContent(getRequest(), streamType, buffer);
        }
        catch (Throwable x)
        {
            logger.debug("Exception while invoking listener " + listener, x);
        }
    }

    protected void onEnd()
    {
        try
        {
            listener.onEnd(getRequest());
        }
        catch (Throwable x)
        {
            logger.debug("Exception while invoking listener " + listener, x);
        }
    }

    private enum State
    {
        LENGTH, CONTENT
    }
}
