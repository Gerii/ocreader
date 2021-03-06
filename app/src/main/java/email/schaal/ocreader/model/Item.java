/*
 * Copyright (C) 2015 Daniel Schaal <daniel@schaal.email>
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

package email.schaal.ocreader.model;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

/**
 * RealmObject representing a feed Item.
 */
public class Item extends RealmObject {
    @PrimaryKey
    private long id;
    public final static String ID = "id";

    private String guid;

    private String guidHash;
    public final static String GUID_HASH = "guidHash";

    private String url;

    private String title;
    public final static String TITLE = "title";

    private String author;

    private Date pubDate;
    public static final String PUB_DATE = "pubDate";

    private String body;
    public static final String BODY = "body";

    private String enclosureMime;
    private String enclosureLink;

    private Feed feed;
    public final static String FEED = "feed";

    private long feedId;
    public final static String FEED_ID = "feedId";

    private boolean unread;
    public final static String UNREAD = "unread";

    private boolean unreadChanged = false;
    public final static String UNREAD_CHANGED = "unreadChanged";

    private boolean starred;
    public static final String STARRED = "starred";

    private boolean starredChanged = false;
    public final static String STARRED_CHANGED = "starredChanged";

    private long lastModified;
    public static final String LAST_MODIFIED = "lastModified";

    /** @since 8.4.0 **/
    @Index
    private String fingerprint;
    public static final String FINGERPRINT = "fingerprint";

    public Item() {
    }

    public Item(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getGuidHash() {
        return guidHash;
    }

    public void setGuidHash(String guidHash) {
        this.guidHash = guidHash;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Date getPubDate() {
        return pubDate;
    }

    public void setPubDate(Date pubDate) {
        this.pubDate = pubDate;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getEnclosureMime() {
        return enclosureMime;
    }

    public void setEnclosureMime(String enclosureMime) {
        this.enclosureMime = enclosureMime;
    }

    public String getEnclosureLink() {
        return enclosureLink;
    }

    public void setEnclosureLink(String enclosureLink) {
        this.enclosureLink = enclosureLink;
    }

    public long getFeedId() {
        return feedId;
    }

    public void setFeedId(long feedId) {
        this.feedId = feedId;
    }

    public Feed getFeed() {
        return feed;
    }

    public void setFeed(Feed feed) {
        this.feed = feed;
    }

    public boolean isUnread() {
        return unread;
    }

    public void setUnread(boolean unread) {
        if(isValid() && this.unread != unread) {
            unreadChanged = !unreadChanged;
            feed.incrementUnreadCount(unread ? 1 : -1);
        }
        this.unread = unread;
    }

    public boolean isStarred() {
        return starred;
    }

    public void setStarred(boolean starred) {
        if(isValid() && this.starred != starred) {
            starredChanged = !starredChanged;
            feed.incrementStarredCount(starred ? 1 : -1);
        }
        this.starred = starred;
    }

    public boolean isUnreadChanged() {
        return unreadChanged;
    }

    public void setUnreadChanged(boolean unreadChanged) {
        this.unreadChanged = unreadChanged;
    }

    public boolean isStarredChanged() {
        return starredChanged;
    }

    public void setStarredChanged(boolean starredChanged) {
        this.starredChanged = starredChanged;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
}
