package com.appendto.hudson.testswarm;

import hudson.model.BuildListener;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TestSwarmDecisionMaker {

	private boolean buildSuccessful = false;
	
    public Map<String, Map<String, Integer>> parseResults(JSONObject json) {

        JSONArray runs = json.getJSONArray("runs");
        int totalRuns = runs.size();

        Map<String, Map<String, Integer>> results = new HashMap<String, Map<String, Integer>>();

        for (int runNum = 0; runNum < totalRuns; runNum++) {
            JSONObject run = runs.getJSONObject(runNum);
            JSONObject runInfo = run.getJSONObject("info");
            String runName = runInfo.getString("name");
            JSONObject userAgents = run.getJSONObject("uaRuns");

            Map<String, Integer> runResults = new HashMap<String, Integer>();

            results.put(runName, runResults);

            for (Iterator iterator = userAgents.keys(); iterator.hasNext(); ) {
                String agentName = (String)iterator.next();

                JSONObject userAgentInfo = userAgents.getJSONObject(agentName);
                String runStatus = userAgentInfo.getString("runStatus");
                if (!runResults.containsKey(runStatus)) {
                    runResults.put(runStatus, 1);
                } else {
                    runResults.put(runStatus, runResults.get(runStatus) + 1);
                }
            }
        }

        return results;
    }

	public boolean finished(Map<String, Map<String, Integer>> runResults, BuildListener listener) {
		
		boolean isFinished = false;
		
		if (runResults.size() == 0) {
			listener.getLogger().println("PROBLEM - NO RESULTS FOUND");
			return true;// fail
		}

        Integer notstarted = 0;
        Integer pass = 0;
        Integer progress = 0;
        Integer error = 0;
        Integer fail = 0;
        Integer timeout = 0;

        for (String runName : runResults.keySet()) {
            Map<String, Integer> results = runResults.get(runName);

            notstarted += results.get("new") != null ? results.get("new") : 0;
            pass += results.get("passed") != null ? results.get("passed") : 0;
            progress += results.get("progress") != null ? results.get("progress") : 0;
            error += results.get("error") != null ? results.get("error") : 0;
            fail += results.get("failed") != null ? results.get("failed") : 0;
            timeout += results.get("timeout") != null ? results.get("timeout") : 0;
        }

		if (error > 0) {
			listener.getLogger().println(error + " test suite" + (error != 1 ? "s" : "") + " ERRORED OUT");
			buildSuccessful = false;
			isFinished = true;
		}

		if (fail > 0) {
			listener.getLogger().println(fail+" test suite" + (fail != 1 ? "s" : "") + " FAILED");
			buildSuccessful = false;
			isFinished = true;
		}

		if (timeout > 0) {
			listener.getLogger().println(timeout+" test suite" + (timeout != 1 ? "s" : "") + " TIMED OUT");
			buildSuccessful = false;
			isFinished = true;
		}

		if (notstarted == 0 && progress == 0 && timeout == 0 && fail == 0 && error == 0 && pass > 0) {
			listener.getLogger().println(pass+" test suite" + (pass != 1 ? "s" : "") + " FINISHED SUCCESSFULLY");
			buildSuccessful = true;
			isFinished = true;
		}

        return isFinished;
    }
	
	public boolean isBuildSuccessful() {
		return buildSuccessful;
	}
}
