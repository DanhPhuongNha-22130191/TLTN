// Copyright (c) 2020, Pavlo Buidenkov. All rights reserved.
// Licensed under the BSD 3-Clause License.
package com.pavlobu.emojitextflow;

import javafx.scene.image.Image;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

public final class EmojiImageCache {
    private static final EmojiImageCache INSTANCE = new EmojiImageCache();
    private final ConcurrentHashMap<String, WeakReference<Image>> images =
            new ConcurrentHashMap<>();

    private EmojiImageCache() {
    }

    public static EmojiImageCache getInstance() {
        return INSTANCE;
    }

    public Image getImage(String path) {
        WeakReference<Image> reference = images.get(path);
        Image image = reference == null ? null : reference.get();
        if (image == null) {
            image = new Image(path, true);
            images.put(path, new WeakReference<>(image));
        }
        return image;
    }
}
