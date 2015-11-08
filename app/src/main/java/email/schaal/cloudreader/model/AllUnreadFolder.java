/*
 * Copyright (C) 2015 Daniel Schaal <daniel@schaal.email>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package email.schaal.cloudreader.model;

import android.content.Context;

import email.schaal.cloudreader.R;

/**
 * Created by daniel on 08.11.15.
 */
public class AllUnreadFolder implements TreeItem, TreeIconable {
    public final static long ID = -10;

    private final Context context;

    public AllUnreadFolder(Context context) {
        this.context = context;
    }

    @Override
    public long getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return context.getString(R.string.unread_items);
    }

    @Override
    public int getIcon() {
        return R.drawable.ic_feed_icon;
    }
}
