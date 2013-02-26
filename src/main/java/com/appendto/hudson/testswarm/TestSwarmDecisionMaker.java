package com.appendto.hudson.testswarm;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestSwarmDecisionMaker {

	private boolean buildSuccessful = false;
	
    public Map<String, Integer> parseResults(JSONObject json) {

        JSONArray runs = json.getJSONArray("runs");
        JSONObject run = runs.getJSONObject(0);
        JSONObject userAgents = run.getJSONObject("uaRuns");
        Map<String, Integer> results = new HashMap<String, Integer>();

        for (Iterator iterator = userAgents.keys(); iterator.hasNext(); ) {
            String agentName = (String)iterator.next();

            JSONObject userAgentInfo = userAgents.getJSONObject(agentName);
            String runStatus = userAgentInfo.getString("runStatus");
            if (!results.containsKey(runStatus)) {
                results.put(runStatus, 1);
            } else {
                results.put(runStatus, results.get(runStatus) + 1);
            }
        }

    	return results;
    }

	public String grabPage(String url) throws IOException {
		
		URL u;
		InputStream is = null;
		DataInputStream dis = null;
		String s;
		StringBuffer result = new StringBuffer();
		
		try {
	
			u = new URL(url);
			is = u.openStream();
			dis = new DataInputStream(new BufferedInputStream(is));
		
			while ((s = dis.readLine()) != null) {
				result.append(s).append("\n");
			}
		} 
		finally {
			if (dis != null)
				dis.close();
			if (is != null)
				is.close();
		}
		return result.toString();
	}
	
	public boolean finished(Map<String, Integer> results, BuildListener listener) {
		
		boolean isFinished = false;
		
		if (results.size() == 0) {
			listener.getLogger().println("PROBLEM - NO RESULTS FOUND");
			return true;// fail
		}

		Integer notstarted = results.get("new");
		Integer pass = results.get("passed");
		Integer progress = results.get("progress");
		Integer error = results.get("error");		
		Integer fail = results.get("failed");
		Integer timeout = results.get("timeout");
		
		if (error != null && error > 0) {
			listener.getLogger().println(error + " test suites ended with ERROR");
			buildSuccessful = false;
			isFinished = true;
		}

		if (fail != null && fail > 0) {
			listener.getLogger().println(fail+" test suites ended with FAILURE");
			buildSuccessful = false;
			isFinished = true;
		}

		if (timeout != null && timeout > 0) {
			listener.getLogger().println(timeout+" test suites ended with TIMED OUT");
			buildSuccessful = false;
			isFinished = true;
		}

		if ((notstarted == null || notstarted == 0)
				&& (progress == null || progress == 0)
				&& (timeout == null || timeout == 0)
					&& (fail == null || fail == 0)
						&& (error == null || error == 0) && pass != null
						&& pass > 0) {
			listener.getLogger().println(pass+" test suites ended with SUCCESS");
			buildSuccessful = true;
			isFinished = true;
		}

        return isFinished;
    }
	
	public boolean isBuildSuccessful() {
		return buildSuccessful;
	}
}
