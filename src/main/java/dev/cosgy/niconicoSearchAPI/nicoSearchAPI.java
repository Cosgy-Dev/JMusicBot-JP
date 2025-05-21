package dev.cosgy.niconicoSearchAPI;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

public class nicoSearchAPI {
    private final boolean cacheEnabled;
    private final int maxCacheSize;
    private LinkedHashMap<String, nicoVideoSearchResult> videoResultCache;

    public nicoSearchAPI(boolean cacheEnabled, int maxCacheSize) {
        this.cacheEnabled = cacheEnabled;
        this.maxCacheSize = maxCacheSize;
    }

    public LinkedList<nicoVideoSearchResult> searchVideo(String query, int resultLimit) {
        return searchVideo(query, resultLimit, true);
    }


    public LinkedList<nicoVideoSearchResult> searchVideo(String query, int resultLimit, boolean autogetVideoInfo) {
        if (resultLimit <= 0) resultLimit = 10;
        if (cacheEnabled && videoResultCache == null) videoResultCache = new LinkedHashMap<>();

        // https://snapshot.search.nicovideo.jp/api/v2/snapshot/video/contents/search?q=初音ミク&targets=title&fields=contentId,title,viewCounter&filters[viewCounter][gte]=10000&_sort=-viewCounter&_offset=0&_limit=3&_context=apiguide
        // https://snapshot.search.nicovideo.jp/api/v2/snapshot/video/contents/search?q=初音ミク&_limit=5&_context=discord_bot&fields=contentId,title,description,tags,categoryTags,viewCounter,mylistCounter,commentCounter,startTime,lastCommentTime,lengthSeconds,thumbnailUrl&_sort=-viewCounter&targets=title

        HTTPUtil hu = new HTTPUtil();
        hu.setTargetAddress("https://snapshot.search.nicovideo.jp/api/v2/snapshot/video/contents/search");
        hu.setMethod("GET");
        Map<String, String> queryMap = new HashMap<>();
        queryMap.put("q", URLEncoder.encode(query, StandardCharsets.UTF_8));
        queryMap.put("_sort", "-viewCounter");
        queryMap.put("targets", "title");
        queryMap.put("fields", "contentId,title,description,userId,channelId,viewCounter,thumbnailUrl,categoryTags,tags,mylistCounter,commentCounter,startTime");
        queryMap.put("_limit", String.valueOf(resultLimit));
        queryMap.put("_context", "discord_bot");
        hu.setQueryMap(queryMap);

        LinkedList<nicoVideoSearchResult> results = new LinkedList<>();
        JSONObject object = new JSONObject(hu.request());

        System.out.println(object.toString(4));

        for (Object resultObject : object.getJSONArray("data")) {
            JSONObject result = (JSONObject) resultObject;

            if (videoResultCache.containsKey(result.getString("contentId"))) {
                System.out.println("[DEBUG] Using cache for VideoID " + result.getString("contentId"));
                results.add(videoResultCache.get(result.getString("contentId")));
            } else {
                nicoVideoSearchResult rs = new nicoVideoSearchResult(
                        result.getString("contentId"),
                        result.getString("title"),
                        result.getString("description"),
                        result.getString("tags").split(" "),
                        (result.isNull("categoryTags") ? new String[0] : result.getString("categoryTags").split(" ")), // FIX categoryTags: null json parse error
                        result.getInt("viewCounter"),
                        result.getInt("mylistCounter"),
                        result.getInt("commentCounter"),
                        result.getString("startTime"),
                        result.getString("thumbnailUrl"),
                        autogetVideoInfo);
                results.add(rs);
                videoResultCache.put(rs.getContentId(), rs);
            }

            if (maxCacheSize >= 1 && videoResultCache.size() > maxCacheSize) {
                videoResultCache.remove(videoResultCache.keySet().toArray()[0]);
            }
        }
        return results;
    }
}

