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

import android.util.Log;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Created by daniel on 10.11.15.
 */
public class FolderTypeAdapter extends TypeAdapter<Folder> {
    private final static String TAG = FolderTypeAdapter.class.getSimpleName();

    @Override
    public void write(JsonWriter out, Folder value) throws IOException {
    }

    @Override
    public Folder read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        Folder folder = new Folder();
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case "id":
                    folder.setId(in.nextLong());
                    break;
                case "name":
                    folder.setTitle(in.nextString());
                    break;
                default:
                    Log.w(TAG, "Unknown value in folder json: " + name);
                    in.skipValue();
                    break;
            }
        }
        in.endObject();
        return folder;
    }
}
