package com.appendto.hudson.testswarm;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TestSwarmUtil {

	private static TestSwarmUtil swarmUtil = null;

    public static JSONObject getAndParseJSON(String httpUrl, AbstractBuild build, BuildListener listener) throws IOException {
        URL url = new URL(httpUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setInstanceFollowRedirects(false);

        InputStreamReader in = new InputStreamReader((InputStream) connection.getContent());
        BufferedReader buff = new BufferedReader(in);
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = buff.readLine()) != null) {
            data.append(line);
//            listener.getLogger().println(line);
        }

        // Parse into JSONObject
        JSONObject json = (JSONObject) JSONSerializer.toJSON(data.toString());

        // Handle errors automatically (this could be an issue if an error is really just a warning
        // and the build continues normally...)
        if (json.has("error")) {
            JSONObject error = json.getJSONObject("error");
            listener.getLogger().println("");
            listener.getLogger().println("[ERROR] An error occurred on the TestSwarm server:");
            listener.getLogger().println("  Code: " + error.getString("code"));
            listener.getLogger().println("  Info: " + error.getString("info"));
            build.setResult(Result.FAILURE);
        }

        return json;
    }


	public StringBuffer processResult(String jobUrl, String testSwarmServerUrl, AbstractBuild build, BuildListener listener) throws IOException {

        // What was the result?
        JSONObject jobResult = TestSwarmUtil.getAndParseJSON(jobUrl, build, listener);
        JSONObject job = jobResult.getJSONObject("job");

		StringBuffer stringBuffer = new StringBuffer();

        JSONArray runs = job.getJSONArray("runs");

        int totalRuns = runs.size();

        Map<String, Map<String, Map<String, String>>> runResults = new HashMap<String, Map<String, Map<String, String>>>();

        stringBuffer.append("\nTest Case Summaries:\n ");

        for (int runNum = 0; runNum < totalRuns; runNum++) {

            JSONObject run = runs.getJSONObject(runNum);

            JSONObject runInfo = run.getJSONObject("info");
            String runName = runInfo.getString("name");

            JSONObject userAgents = run.getJSONObject("uaRuns");

            Map<String, Map<String, String>> results = new HashMap<String, Map<String, String>>();
            runResults.put(runName, results);

            results.put("NOT STARTED", new HashMap<String, String>());
            results.put("PASSED", new HashMap<String, String>());
            results.put("FAILED", new HashMap<String, String>());
            results.put("RUNNING", new HashMap<String, String>());

            for (Iterator iterator = userAgents.keys(); iterator.hasNext(); ) {
                String agentName = (String)iterator.next();

                JSONObject userAgentInfo = userAgents.getJSONObject(agentName);
                String runStatus = userAgentInfo.getString("runStatus");

                String resultUrl = userAgentInfo.optString("runResultsUrl", null);
                if (resultUrl != null) {
                    resultUrl = resultUrl.replaceAll("testswarm/","");
                } else {
                    resultUrl = "";
                }

                if (runStatus.equals("failed")) {
                    results.get("FAILED").put(agentName, resultUrl);
                } else if (runStatus.equals("passed")) {
                    results.get("PASSED").put(agentName, resultUrl);
                } else if (runStatus.equals("new")) {
                    results.get("NOT STARTED").put(agentName, resultUrl);
                } else if (runStatus.equals("progress")) {
                    results.get("RUNNING").put(agentName, resultUrl);
                }
            }
        }

        for (String suite : runResults.keySet()) {

            Map <String, Map<String, String>> suiteResults = runResults.get(suite);
            stringBuffer.append("\nTest Suite: ").append(suite).append("\n");

            for (String status : suiteResults.keySet()) {
                if (suiteResults.get(status).size() > 0) {
                    stringBuffer.append(status).append("\n");
                    for (String userAgent : suiteResults.get(status).keySet()) {
                        String url = suiteResults.get(status).get(userAgent);

                        stringBuffer.append("       ").append(userAgent);
                        if (!url.equals("")) {
                            stringBuffer.append(": ").append(testSwarmServerUrl).append(url);
                        }

                        stringBuffer.append("\n");
                    }
                }
            }
        }

		return stringBuffer;
	}

	private TestSwarmUtil() {

	}

	public static TestSwarmUtil getInstance() {
		if (swarmUtil == null)
			swarmUtil = new TestSwarmUtil();

		return swarmUtil;
	}
}
