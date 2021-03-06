package email.schaal.ocreader.database;

import android.util.Log;

import email.schaal.ocreader.model.Feed;
import email.schaal.ocreader.model.Folder;
import email.schaal.ocreader.model.Item;
import io.realm.DynamicRealm;
import io.realm.DynamicRealmObject;
import io.realm.RealmList;
import io.realm.RealmMigration;
import io.realm.RealmObjectSchema;
import io.realm.RealmSchema;

/**
 * RealmMigration to migrate database between schema versions
 */
class DatabaseMigration implements RealmMigration {
    private static final String TAG = DatabaseMigration.class.getName();

    @Override
    public void migrate(final DynamicRealm realm, long oldVersion, long newVersion) {
        Log.d(TAG, "Starting migration from " + oldVersion + "to " + newVersion);

        RealmSchema schema = realm.getSchema();

        /**
         * v1 -> v2
         * - Add unreadChanged and starredChanged boolean fields to Item
         * - Migrate data from changedItems to new fields
         * - Remove ChangedItems
         */
        if (oldVersion == 1) {
            DynamicRealmObject changedItems = realm.where("ChangedItems").findFirst();
            final RealmList<DynamicRealmObject> unreadChangedItems = changedItems.getList("unreadChangedItems");
            final RealmList<DynamicRealmObject> starredChangedItems = changedItems.getList("starredChangedItems");

            schema.get("Item")
                    .addField(Item.UNREAD_CHANGED, boolean.class)
                    .addField(Item.STARRED_CHANGED, boolean.class);

            for (DynamicRealmObject item : unreadChangedItems) {
                item.set(Item.UNREAD_CHANGED, true);
            }
            for (DynamicRealmObject item : starredChangedItems) {
                item.set(Item.STARRED_CHANGED, true);
            }

            schema.remove("ChangedItems");
            oldVersion++;
        }

        /**
         * v2 -> v3
         * - Add indexed fingerprint field to Item
         */
        if (oldVersion == 2) {
            schema.get("Item")
                    .addField(Item.FINGERPRINT, String.class)
                    .addIndex(Item.FINGERPRINT);
            oldVersion++;
        }

        /**
         * v3 -> v4
         * - Add feed field to Item
         */
        if (oldVersion == 3) {
            schema.get("Item")
                    .addRealmObjectField(Item.FEED, schema.get("Feed"));
            for (DynamicRealmObject item : realm.where("Item").findAll()) {
                item.setObject(Item.FEED, realm.where("Feed").equalTo(Feed.ID, item.getLong(Item.FEED_ID)).findFirst());
            }
            oldVersion++;
        }

        /**
         * v4 -> v5
         * - remove color field from Feed
         */
        if (oldVersion == 4) {
            schema.get("Feed").removeField("color");
            oldVersion++;
        }

        /**
         * v5 -> v6
         * - Migrate Item.LAST_MODIFIED from Date to long
         */
        if (oldVersion == 5) {
            schema.get("Item")
                    .renameField(Item.LAST_MODIFIED, "lastModifiedDate")
                    .addField(Item.LAST_MODIFIED, long.class)
                    .transform(new RealmObjectSchema.Function() {
                        @Override
                        public void apply(DynamicRealmObject obj) {
                            obj.setLong(Item.LAST_MODIFIED, obj.getDate("lastModifiedDate").getTime() / 1000);
                        }
                    })
                    .removeField("lastModifiedDate");

            oldVersion++;
        }

        /**
         * v6 -> v7
         * - Add lastUpdateError and updateErrorCount to Feed
         */
        if (oldVersion == 6) {
            schema.get("Feed")
                    .addField(Feed.LAST_UPDATE_ERROR, String.class)
                    .addField(Feed.UPDATE_ERROR_COUNT, int.class);

            oldVersion++;
        }

        /**
         * v7 -> v8
         * - Add folder field to Feed
         */
        if (oldVersion == 7) {
            schema.get("Feed")
                    .addRealmObjectField(Feed.FOLDER, schema.get("Folder"))
                    .transform(new RealmObjectSchema.Function() {
                        @Override
                        public void apply(DynamicRealmObject obj) {
                            obj.setObject(Feed.FOLDER, realm.where("Folder")
                                    .equalTo(Folder.ID, obj.getLong(Feed.FOLDER_ID)).findFirst());
                        }
                    });
            oldVersion++;
        }
    }
}
