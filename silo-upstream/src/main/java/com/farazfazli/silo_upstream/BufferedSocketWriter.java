package com.farazfazli.silo_upstream;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * Created by farazfazli on 9/30/16.
 */

public class BufferedSocketWriter extends BufferedWriter {
    public BufferedSocketWriter(Writer out) {
        super(out);
    }

    /**
     * Utility method to send a JSONObject containing the Device ID and
     * the message, BufferedWriter is used instead of PrintWriter because
     * PrintWriter swallows exceptions, an undesired behavior.
     * @param jsonObject
     * @throws IOException
     */
    void writeJson(JSONObject jsonObject) throws IOException {
        super.write(jsonObject.toString()+"\n");
    }

    /**
     * Sends a JSON message, and flushes the stream.
     * @param jsonObject
     * @throws IOException
     */
    public void sendJson(JSONObject jsonObject) throws IOException {
        writeJson(jsonObject);
        flush();
    }
}
