/*
 * Copyright (C) 2015-2016 Daniel Schaal <daniel@schaal.email>
 *
 * This file is part of OCReader.
 *
 * OCReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OCReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OCReader.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package email.schaal.ocreader.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import email.schaal.ocreader.Preferences;
import email.schaal.ocreader.api.json.Feeds;
import email.schaal.ocreader.api.json.Folders;
import email.schaal.ocreader.api.json.ItemIds;
import email.schaal.ocreader.api.json.ItemMap;
import email.schaal.ocreader.api.json.Items;
import email.schaal.ocreader.api.json.Status;
import email.schaal.ocreader.database.Queries;
import email.schaal.ocreader.http.HttpManager;
import email.schaal.ocreader.model.Feed;
import email.schaal.ocreader.model.FeedTypeAdapter;
import email.schaal.ocreader.model.Folder;
import email.schaal.ocreader.model.FolderTypeAdapter;
import email.schaal.ocreader.model.Item;
import email.schaal.ocreader.model.ItemTypeAdapter;
import email.schaal.ocreader.model.NewsError;
import email.schaal.ocreader.model.StatusTypeAdapter;
import email.schaal.ocreader.model.User;
import email.schaal.ocreader.model.UserTypeAdapter;
import email.schaal.ocreader.util.AlarmUtils;
import io.realm.Realm;
import io.realm.RealmResults;
import okhttp3.HttpUrl;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * This class encapsulates the Nextcloud News API and communicates with the remote ownCloud instance.
 */
public class APIService {
    private static final String TAG = APIService.class.getName();

    private static final String ROOT_PATH_APIv1_2 = "./index.php/apps/news/api/v1-2/";

    private static final int BATCH_SIZE = 100;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MoshiConverterFactory converterFactory;
    private final JsonAdapter<NewsError> errorJsonAdapter;

    public void setHttpManager(HttpManager httpManager) {
        api = setupApi(httpManager);
    }

    private enum MarkAction {
        MARK_READ(Item.UNREAD, Item.UNREAD_CHANGED, false),
        MARK_UNREAD(Item.UNREAD, Item.UNREAD_CHANGED, true),
        MARK_STARRED(Item.STARRED, Item.STARRED_CHANGED, true),
        MARK_UNSTARRED(Item.STARRED, Item.STARRED_CHANGED, false);

        private final String key;
        private final String changedKey;
        private final boolean value;

        public String getKey() {
            return key;
        }

        public String getChangedKey() {
            return changedKey;
        }

        public boolean getValue() {
            return value;
        }

        MarkAction(String key, String changedKey, boolean value) {
            this.key = key;
            this.changedKey = changedKey;
            this.value = value;
        }
    }

    public interface OnCompletionListener {
        void onCompleted(boolean result);
    }

    private abstract class ResultRunnable implements Runnable {
        protected final boolean result;

        public ResultRunnable(boolean result) {
            this.result = result;
        }
    }

    public void syncChanges(@Nullable final OnCompletionListener completionListener) {
        AlarmUtils.getInstance().cancelAlarm();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                Realm realm = null;
                boolean result = true;
                try {
                    realm = Realm.getDefaultInstance();
                    for (final MarkAction action : MarkAction.values()) {
                        result = result && markItems(action, realm);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    Queries.closeRealm(realm);
                    handler.post(new ResultRunnable(result) {
                        @Override
                        public void run() {
                            if (completionListener != null)
                                completionListener.onCompleted(this.result);
                        }
                    });
                }
            }
        });
    }

    private boolean markItems(@NonNull final MarkAction action, final Realm realm) throws IOException {
        final RealmResults<Item> results = realm.where(Item.class)
                .equalTo(action.getChangedKey(), true)
                .equalTo(action.getKey(), action.getValue()).findAll();

        if (results.size() == 0) {
            // Nothing to do, countdown and return
            return true;
        }

        ItemIds ids = null;
        ItemMap itemMap = null;

        if (action == MarkAction.MARK_UNREAD || action == MarkAction.MARK_READ) {
            ids = new ItemIds(results);
        } else {
            itemMap = new ItemMap(results);
        }

        Response<Void> response;

        switch (action) {
            case MARK_READ:
                response = api.markItemsRead(ids).execute();
                break;
            case MARK_UNREAD:
                response = api.markItemsUnread(ids).execute();
                break;
            case MARK_STARRED:
                response = api.markItemsStarred(itemMap).execute();
                break;
            case MARK_UNSTARRED:
                response = api.markItemsUnstarred(itemMap).execute();
                break;
            default:
                throw new IllegalArgumentException("Unkown mark action");
        }

        if (response.isSuccessful()) {
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    if (action == MarkAction.MARK_READ || action == MarkAction.MARK_UNREAD) {
                        for (Item item : results) {
                            item.setUnreadChanged(false);
                        }
                    } else {
                        for (Item item : results) {
                            item.setStarredChanged(false);
                        }
                    }
                }
            });
        }

        return response.isSuccessful();
    }

    public enum QueryType {
        FEED(0),
        FOLDER(1),
        STARRED(2),
        ALL(3);

        private final int type;

        QueryType(int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }
    }

    private static APIService instance;

    private API api;

    public static void init(Context context) {
        instance = new APIService(context);
    }

    public static APIService getInstance() {
        if(instance == null)
            throw new IllegalStateException("initialize first");
        return instance;
    }

    private APIService(Context context) {
        final Moshi moshi = new Moshi.Builder()
                .add(Folder.class, new FolderTypeAdapter())
                .add(Feed.class, new FeedTypeAdapter())
                .add(Item.class, new ItemTypeAdapter())
                .add(User.class, new UserTypeAdapter())
                .add(Status.class, new StatusTypeAdapter())
                .build();

        converterFactory = MoshiConverterFactory.create(moshi);

        errorJsonAdapter = moshi.adapter(NewsError.class);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String username = Preferences.USERNAME.getString(sharedPreferences);
        if(username != null) {
            String password = Preferences.PASSWORD.getString(sharedPreferences);
            String url = Preferences.URL.getString(sharedPreferences);
            api = setupApi(new HttpManager(username, password, HttpUrl.parse(url)));
        }
    }

    public API setupApi(HttpManager httpManager) {
        HttpUrl baseUrl = httpManager.getCredentials().getRootUrl().resolve(ROOT_PATH_APIv1_2);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(httpManager.getClient())
                .addConverterFactory(converterFactory)
                .build();

        return retrofit.create(API.class);
    }

    @SuppressWarnings("SameParameterValue")
    public interface API {
        /** SERVER **/

        /** Since 6.0.5 **/
        @GET("user")
        Call<User> user();

        @GET("status")
        Call<Status> status();

        /** FOLDERS **/
        @GET("folders")
        Call<Folders> folders();

        /** FEEDS **/
        @GET("feeds")
        Call<Feeds> feeds();

        @POST("feeds")
        Call<Feeds> createFeed(@Body Map<String, Object> feedMap);

        @PUT("feeds/{feedId}/move")
        Call<Void> moveFeed(@Path("feedId") long feedId, @Body Map<String,Long> folderIdMap);

        @DELETE("feeds/{feedId}")
        Call<Void> deleteFeed(@Path("feedId") long feedId);

        /** ITEMS **/
        @GET("items")
        Call<Items> items(
                @Query("batchSize") long batchSize,
                @Query("offset") long offset,
                @Query("type") int type,
                @Query("id") long id,
                @Query("getRead") boolean getRead,
                @Query("oldestFirst") boolean oldestFirst
        );

        @GET("items/updated")
        Call<Items> updatedItems(
                @Query("lastModified") long lastModified,
                @Query("type") int type,
                @Query("id") long id
        );

        @PUT("items/read/multiple")
        Call<Void> markItemsRead(@Body ItemIds items);

        @PUT("items/unread/multiple")
        Call<Void> markItemsUnread(@Body ItemIds items);

        @PUT("items/star/multiple")
        Call<Void> markItemsStarred(@Body ItemMap itemMap);

        @PUT("items/unstar/multiple")
        Call<Void> markItemsUnstarred(@Body ItemMap itemMap);
    }

    public void user(final Realm realm, final APICallback callback) {
        api.user().enqueue(new BaseRetrofitCallback<User>(callback) {
            @Override
            protected boolean onResponseReal(Response<User> response) {
                Queries.insert(realm, User.class, response.body());
                return true;
            }
        });
    }

    public void items(final Realm realm, final APICallback callback) {
        api.items(-1, 0L, QueryType.ALL.getType(), 0L, false, false).enqueue(new BaseRetrofitCallback<Items>(callback) {
            @Override
            public boolean onResponseReal(Response<Items> response) {
                final List<Item> items = response.body().getItems();

                Queries.insert(realm, Item.class, items);

                return true;
            }
        });
    }

    public void updatedItems(final Realm realm, long lastSync, final APICallback callback) {
        api.updatedItems(lastSync, QueryType.ALL.getType(), 0L).enqueue(new BaseRetrofitCallback<Items>(callback) {
            @Override
            protected boolean onResponseReal(Response<Items> response) {
                List<Item> items = response.body().getItems();
                Queries.insert(realm, Item.class, items);
                return true;
            }
        });
    }

    public void starredItems(final Realm realm, final APICallback callback) {
        api.items(-1, 0L, QueryType.STARRED.getType(), 0L, true, false).enqueue(new BaseRetrofitCallback<Items>(callback) {
            @Override
            protected boolean onResponseReal(Response<Items> response) {
                final List<Item> items = response.body().getItems();

                Queries.insert(realm, Item.class, items);
                return true;
            }
        });
    }

    public void moreItems(final Realm realm, final QueryType type, final long offset, final long id, final APICallback callback) {
        api.items(BATCH_SIZE, offset, type.getType(), id, true, false).enqueue(new BaseRetrofitCallback<Items>(callback) {
            @Override
            public boolean onResponseReal(Response<Items> response) {
                final List<Item> items = response.body().getItems();
                Queries.insert(realm, Item.class, items);
                return true;
            }
        });
    }

    public void folders(final Realm realm, final APICallback callback) {
        api.folders().enqueue(new BaseRetrofitCallback<Folders>(callback) {
            @Override
            public boolean onResponseReal(Response<Folders> response) {
                List<Folder> folders = response.body().getFolders();

                Queries.deleteAndInsert(realm, Folder.class, folders);
                return true;
            }
        });
    }

    public void feeds(final Realm realm, final APICallback callback) {
        api.feeds().enqueue(new BaseRetrofitCallback<Feeds>(callback) {
            @Override
            protected boolean onResponseReal(Response<Feeds> response) {
                Feeds feedsBody = response.body();
                List<Feed> feeds = feedsBody.getFeeds();

                Queries.deleteAndInsert(realm, Feed.class, feeds);

                return true;
            }
        });
    }

    public void createFeed(final Realm realm, final String url, final long folderId, APICallback apiCallback) {
        Map<String, Object> feedMap = new HashMap<>(2);

        feedMap.put("url", url);
        feedMap.put("folderId", folderId);

        api.createFeed(feedMap).enqueue(new BaseRetrofitCallback<Feeds>(apiCallback) {
            @Override
            protected boolean onResponseReal(final Response<Feeds> response) {
                // Set unreadCount to 0, items have not been fetched yet for this feed
                Feed feed = response.body().getFeeds().get(0);
                feed.setUnreadCount(0);

                Queries.insert(realm, Feed.class, feed);

                return true;
            }
        });
    }

    public void moveFeed(final Realm realm, final Feed feed, final long folderId, APICallback apiCallback) {
        Map<String, Long> folderIdMap = new HashMap<>(1);
        folderIdMap.put("folderId", folderId);

        api.moveFeed(feed.getId(), folderIdMap).enqueue(new BaseRetrofitCallback<Void>(apiCallback) {
            @Override
            protected boolean onResponseReal(Response<Void> response) {
                realm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        feed.setFolderId(folderId);
                    }
                });
                return true;
            }
        });
    }

    public void deleteFeed(final Realm realm, final Feed feed, APICallback apiCallback) {
        api.deleteFeed(feed.getId()).enqueue(new BaseRetrofitCallback<Void>(apiCallback) {
            @Override
            protected boolean onResponseReal(Response<Void> response) {
                Queries.deleteFeed(realm, feed);
                return true;
            }
        });
    }
    public interface APICallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    private abstract class BaseRetrofitCallback<T> implements Callback<T> {
        final APICallback callback;

        public BaseRetrofitCallback(APICallback callback) {
            this.callback = callback;
        }

        @Override
        public final void onResponse(Call<T> call, Response<T> response) {
            if (response.isSuccessful()) {
                if (onResponseReal(response))
                    callback.onSuccess();
            } else {
                String message = response.message();
                try {
                    NewsError error = errorJsonAdapter.fromJson(response.errorBody().source());
                    message = error.message;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                callback.onFailure(String.format(Locale.US, "%d: %s", response.code(), message));
            }
        }

        /**
         * Handle the response
         *
         * @param response Retrofit response
         * @return true iff sync event is finished, false iff another run is necessary.
         */
        protected abstract boolean onResponseReal(Response<T> response);

        @Override
        public void onFailure(Call<T> call, Throwable t) {
            t.printStackTrace();
            callback.onFailure(t.getLocalizedMessage());
        }
    }

}
