// Copyright (c) 2020, Pavlo Buidenkov. All rights reserved.
// Licensed under the BSD 3-Clause License.
package com.pavlobu.emojitextflow;

public final class Emoji {
    private final String shortname;
    private final String unicode;
    private final String hex;

    public Emoji(String shortname, String unicode, String hex) {
        this.shortname = shortname;
        this.unicode = unicode;
        this.hex = hex;
    }

    public String getShortname() {
        return shortname;
    }

    public String getUnicode() {
        return unicode;
    }

    public String getHex() {
        return hex;
    }
}
