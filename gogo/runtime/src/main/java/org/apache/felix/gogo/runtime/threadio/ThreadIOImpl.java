/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
// DWB20: ThreadIO should check and reset IO if something (e.g. jetty) overrides
package org.apache.felix.gogo.runtime.threadio;

import org.osgi.service.threadio.ThreadIO;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class ThreadIOImpl implements ThreadIO
{
    static private final Logger log = Logger.getLogger(ThreadIOImpl.class.getName());

    static class Streams {
        ThreadInputStream in = new ThreadInputStream(this, System.in);
        ThreadPrintStream err = new ThreadPrintStream(System.err);
        ThreadPrintStream out = new ThreadPrintStream(System.out);
        AtomicInteger retained = new AtomicInteger(1);
    }

    ThreadLocal<Marker> current = new InheritableThreadLocal<Marker>();
    Streams streams;

    public void start()
    {
        if (System.in instanceof ThreadInputStream)
        {
            ThreadInputStream in = (ThreadInputStream) System.in;
            streams = in.tracker;
            streams.retained.incrementAndGet();
        } else {
            streams = new Streams();
            System.setOut(streams.out);
            System.setIn(streams.in);
            System.setErr(streams.err);
        }
    }

    public void stop()
    {
        if( streams.retained.decrementAndGet()==0 ) {
            System.setErr(streams.err.dflt);
            System.setOut(streams.out.dflt);
            System.setIn(streams.in.dflt);
        }
    }

    private void checkIO()
    { // derek
        if (System.in != streams.in)
        {
            log.fine("ThreadIO: eek! who's set System.in=" + System.in);
            System.setIn(streams.in);
        }

        if (System.out != streams.out)
        {
            log.fine("ThreadIO: eek! who's set System.out=" + System.out);
            System.setOut(streams.out);
        }

        if (System.err != streams.err)
        {
            log.fine("ThreadIO: eek! who's set System.err=" + System.err);
            System.setErr(streams.err);
        }
    }

    public void close()
    {
        checkIO(); // derek
        Marker top = this.current.get();
        if (top == null)
        {
            throw new IllegalStateException("No thread io active");
        }

        Marker previous = top.previous;
        if (previous == null)
        {
            streams.in.end();
            streams.out.end();
            streams.err.end();
        }
        else
        {
            this.current.set(previous);
            previous.activate();
        }
    }

    public void setStreams(InputStream in, PrintStream out, PrintStream err)
    {
        assert in != null;
        assert out != null;
        assert err != null;
        checkIO(); // derek
        Marker marker = new Marker(this, in, out, err, current.get());
        this.current.set(marker);
        marker.activate();
    }
}
