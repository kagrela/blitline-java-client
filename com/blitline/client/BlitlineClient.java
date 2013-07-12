/*
 * Blitline Client Package Version 1.1
 */

package com.blitline.client;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Static class for submitting jobs the Blitline.
 * <p> 
 * BlitlineClient has a dependency on simple-json and google's
 * http libraries.
 * @author blitline_developer
 */
public class BlitlineClient{

    static final JSONParser PARSER = new JSONParser();
    static final String BLITLINE_URL = "http://api.blitline.com/job";
    static final DefaultHttpClient CLIENT = new DefaultHttpClient();
    
    /**
     * This takes a BlitlineResult object and uses Blitline.com's longpolling
     * functionality to wait for Blitline to finish processing the job. This
     * is primarily a developmental tool, and not a production feature. You should
     * use the "Postback" functionality inherent in Blitline to handle notifications
     * of when you job has completed.
     * 
     * @param blitlineResult Result from the submitToBlitline function
     * @return BlitlinePostback The data generated after Blitline has completed the job.
     */
    public static BlitlinePostback longPoll(BlitlineResult blitlineResult) {
        if (blitlineResult.hasError() || blitlineResult.getJobId() == null || blitlineResult.getJobId().length() == 0) {
            return null;
        }
        String result;
        String longpollingUriString = "http://cache.blitline.com/listen/" + blitlineResult.getJobId();
        HttpGet httpget = new HttpGet(longpollingUriString);
        
        // Execute call
        try {
            HttpResponse response = CLIENT.execute(httpget);
            HttpEntity entity = response.getEntity();
            result = EntityUtils.toString(entity, "UTF-8");
            System.out.println( "LONGPOLLING=" + result.toString());
            int code = response.getStatusLine().getStatusCode();
            System.out.println( "LONGPOLLING=" + Integer.toString(code));
        } catch (Exception ex) {
            result = "{\"results\":\"{\\\"images\\\":[{\\\"error\\\":\\\"Exception during longpoll-" + ex.getMessage() + ". Please make sure you are not using a postback URL in your JSON. If there is a postback URL you CANNOT longpoll. Also, if your blitline job submission failed, there will be no longpoll result.\\\"}],\\\"job_id\\\":\\\"" + blitlineResult.getJobId() + "\\\"}\"}";
        }
        
        return new BlitlinePostback(result);
    }
    
    /**
     * This is the primary function for submitting jobs the Blitline.
     * The <code>jsonJob</code> param is assumed to be generated by hand
     * by using the simple-json library. 
     * Example:
     * 
     * <code>
     *  String app_id = "MY_APP_ID";
     *  String src = "http://www.google.com/logos/2011/yokoyama11-hp.jpg";
     *
     *  JSONObject job=new JSONObject();
     *  
     *  job.put("application_id", app_id);
     *  job.put("src", src);
     *  job.put("messedUp", new JSONArray() {{
     *     add( new JSONObject() {{
     *          put("name", "blur"); 
     *          put("params", new JSONObject() {{
     *              put("radius", .40);
     *              put("signma", 1.0);
     *          }});
     *          put("save", new JSONObject() {{
     *              put("image_identifier", "my_silly_image");                      
     *          }});
     *      }}); 
     *   }});
     *   JSONObject json = new JSONObject();
     *   json.put("json", job);
     *   BlitlineClient.submitJsonToBlitline(json.toString());
     * </code>
     * @param json The JSON to submit to Blitline
     * @return The BlitlineResult representing the JSON result from Blitline.com
     */
    public static BlitlineResult submitJsonToBlitline(String json) {
        HttpPost httppost = new HttpPost(BLITLINE_URL);
        httppost.addHeader("Content-Type", "application/json");
   
        String result;
        int serverCode = -1;
        
        // Make sure JSON is parseable
        try {
            httppost.setEntity(new StringEntity(json));               
        }catch(Exception ex) {
            return new BlitlineResult("FAILED", -1, "Unsupported encoding for JSON submitted", null);
        }
        
        // Execute call
        try {
            HttpResponse response = CLIENT.execute(httppost);
            serverCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            result = EntityUtils.toString(entity, "UTF-8");
        } catch (Exception ex) {
            System.out.println("Exception");
            result = "{ \"error\" : \"" + ex.getMessage() + "\"}";            
        }        
        JSONObject jsonObjectResult;
        
        // Parse response results
        try {
            jsonObjectResult = (JSONObject)PARSER.parse(result.toString());
        } 
        catch(ParseException pex) {
           System.out.println(pex.getMessage());
           return new BlitlineResult("FAILED", -1, "Blitline POST FAILURE", null);
        }
        
        // Turn into BlitlineResult
        return parseResults(jsonObjectResult, serverCode);
    }
    
    private static boolean isLongPolling(JSONObject jobJsonObject) {
        Object longPollingNode = jobJsonObject.get("long_polling");
        if (longPollingNode != null && longPollingNode.toString().toLowerCase().equals("true")) {
            return true;
        }
        return false;
    }
    
    private static BlitlineResult parseResults(JSONObject jsonObjectResult, int serverCode) {
        ArrayList imageUrls = new ArrayList();
        String errorMessage = "No Error";
        String jobID = "";
        JSONObject results = new JSONObject();

        if (jsonObjectResult.containsKey("results")) {
            results = (JSONObject)jsonObjectResult.get("results");
            if (results.containsKey("error")) {
                errorMessage = results.get("error").toString();
                serverCode = 500;
            }
            if (results.containsKey("job_id")) {
                jobID = results.get("job_id").toString();                
            }
        }else if (jsonObjectResult.containsKey("error")){
            errorMessage = results.get("error").toString();
        }
               
        ArrayList<HashMap<String, String>> imageArrayList = new ArrayList<HashMap<String, String>>();
        if (results.containsKey("images")) {
            JSONArray urlsList = (JSONArray)results.get("images");
            Iterator it = urlsList.iterator();
            while(it.hasNext()) {
                JSONObject image = (JSONObject)it.next();
                HashMap<String, String> imageHash = BlitlineUtils.convertJSONObjectToHashMap(image);
                imageArrayList.add(imageHash);
            }
            imageUrls = (ArrayList)urlsList;
        }
                                
       return new BlitlineResult(jobID, serverCode, errorMessage, imageArrayList);
    }    
}
