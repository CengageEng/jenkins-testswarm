package com.appendto.hudson.testswarm;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.model.Result;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;

public class TestSwarmUtil {

	private static TestSwarmUtil swarmUtil = null;
	public static void main(String[] args) {
		try {

			String url = "http://swarm.example.com/job/001/";
			String html = new TestSwarmDecisionMaker().grabPage(url);
			//System.out.println(new TestSwarmUtil().getGridText(html));
			TestSwarmUtil testSwarmUtil = new TestSwarmUtil().getInstance();
			System.out.println("Result: \n" + testSwarmUtil.processResult(url, "", null, null));
		}
		catch(Exception ex) {
			ex.printStackTrace();
		}
	}

    public static JSONObject getAndParseJSON(String httpUrl, AbstractBuild build, BuildListener listener) throws IOException {
        URL url = new URL(httpUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setInstanceFollowRedirects(false);

        InputStreamReader in = new InputStreamReader((InputStream) connection.getContent());
        BufferedReader buff = new BufferedReader(in);
        StringBuilder data = new StringBuilder();
        String line;
        do {
            line = buff.readLine();
            data.append(line);
        } while (line != null);

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
        JSONObject run = runs.getJSONObject(0);
        JSONObject userAgents = run.getJSONObject("uaRuns");

        stringBuffer.append("\nTest Case(s) Summary:\n ");

        Map<String, HashMap> results = new HashMap<String, HashMap>();

        results.put("Browsers without Results", new HashMap<String, String>());
        results.put("Passed Browsers", new HashMap<String, String>());
        results.put("Failed Browsers", new HashMap<String, String>());
        results.put("Pending Browsers", new HashMap<String, String>());

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
                results.get("Failed Browsers").put(agentName, resultUrl);
            } else if (runStatus.equals("passed")) {
                results.get("Passed Browsers").put(agentName, resultUrl);
            } else if (runStatus.equals("new")) {
                results.get("Browsers without Results").put(agentName, resultUrl);
            } else if (runStatus.equals("progress")) {
                results.get("Pending Browsers").put(agentName, resultUrl);
            }
        }

        for (String status : results.keySet()) {
            if (results.get(status).size() > 0) {
                stringBuffer.append("\n").append(status).append(": -----------------\n");
                for (Object o : results.get(status).keySet()) {
                    String ua = (String) o;
                    String url = (String) (results.get(status).get(ua));

                    stringBuffer.append("   ").append(ua);
                    if (!url.equals("")) {
                        stringBuffer.append(": ").append(testSwarmServerUrl).append(url);
                    }

                    stringBuffer.append("\n");
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

	private String getGridHTML(String html){


		String result = "";

		String browseRegrex = "(?s)<table class=\"results\">(.*?)</table>";
		//String browseName = ""
		Pattern browser = Pattern.compile(browseRegrex);
		Matcher brMatch = browser.matcher(html);

		if(brMatch.find()) {
			result = brMatch.group();
		}

		return result;
	}

	private List<String> getBrowserList(String html){

		List<String> list = new ArrayList<String>();
		String result = null;

		String browseRegrex = "(?s)<th><div class=\"browser\">(.*?)</div></th>";
		String titleRegex="(?s)title=\"(.*?)\"";
		//String browseName = ""
		Pattern browser = Pattern.compile(browseRegrex);
		Pattern titlePattern = Pattern.compile(titleRegex);
		Matcher brMatch = browser.matcher(html);

		while (brMatch.find()) {

			result = brMatch.group();

			Matcher titleMatch = titlePattern.matcher(result);
			if(titleMatch.find()){
				String title = titleMatch.group();
				title = title.substring(title.indexOf("\"") + 1, title.lastIndexOf("\""));
				list.add(title);
			}
		}
		return list;
	}

	private List<String> getModulesList(String html){

		String result = null;
		List<String> results = new ArrayList<String>();
		String browseRegrex = "(?s)<tr><th><a href(.*?)</tr>";
		//String browseName = ""
		Pattern browser = Pattern.compile(browseRegrex);
		Matcher brMatch = browser.matcher(html);
		Html2text parser = new Html2text();
		Reader in  = null;

		while (brMatch.find()) {

			result = brMatch.group();
			//result = result.replaceAll("</td>","|</td>");
			result = result.replaceAll("<td class='","<td class=''>|");
			result = result.replaceAll("\\&nbsp;</td>", "</td>");
			//result = result.replaceAll(" ","-");
			in  = new StringReader(result);

			try {
				parser.parse(in);
				in.close();
				String resultRow = parser.getText();
				resultRow = resultRow.replaceAll("'>", "-");
				resultRow = resultRow.replaceAll("notstarted-", "notstarted-0");
				//resultRow = resultRow.replaceAll("<#50>", "0");
				results.add(resultRow);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		return results;

	}

	public String getGridText(String html){

		try {

			String gridHtml = getGridHTML(html);

			//Now use gridHtml to get the grid contents-//Construct the header first
			List<String>  browsersList = getBrowserList(gridHtml);
			List<String>  modulesList = getModulesList(gridHtml);
			StringBuilder gridText = new StringBuilder();				
			gridText.append("\n");
			//					gridText.append(StringUtils.rightPad("", 30, ' '));
			browsersList.add(0,StringUtils.rightPad("", 30, ' '));
			//					gridText.append("|");

			for(String browser: browsersList){
				gridText.append(StringUtils.rightPad(browser, 30, ' '));
				gridText.append("|");
			}

			gridText.append("\n\n");
			//Construct the table
			for(String modules: modulesList){

				StringTokenizer tokenizer = new StringTokenizer(modules, "|");
				int count = 0;

				String item;
				while(tokenizer.hasMoreTokens()){
					item = tokenizer.nextToken();
					//gridText.append(StringUtils.rightPad(item, browsersList.get(count).length(), ' '));
					gridText.append(StringUtils.rightPad(item, 30, ' '));
					gridText.append("|");
					count++;
				}	

				gridText.append("\n");
			}

			return gridText.toString();

		}
		catch(Exception ex) {
			ex.printStackTrace();
		}
		return "";
	}


}
