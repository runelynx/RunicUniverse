package io.github.runelynx.runicuniverse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public final class GoogleShortener {
    public static String shorten(String longUrl) {
        if (longUrl == null) {
            return longUrl;
        }
        try {
            URL url = new URL("http://goo.gl/api/url");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "toolbar");
            

            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write("url=" + URLEncoder.encode(longUrl, "UTF-8"));
            writer.close();

            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                sb.append(line).append('\n');
            }

            String json = sb.toString();
            //It extracts easily...
            return json.substring(json.indexOf("http"), json.indexOf("\"", json.indexOf("http")));
        } catch (IOException e) {
            return longUrl;
        }
    }
}
