/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kaltura.dtg.parser.source.hls.playlist;

import com.kaltura.dtg.parser.Format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Represents an HLS master playlist. */
public final class HlsMasterPlaylist extends HlsPlaylist {

  /**
   * Represents a url in an HLS master playlist.
   */
  public static final class HlsUrl {

    /**
     * The http url from which the media playlist can be obtained.
     */
    public final String url;
    /**
     * Format information associated with the HLS url.
     */
    public final Format format;
    public final int firstLineNum;
    public final int lastLineNum;

    /**
     * @param url See {@link #url}.
     * @param format See {@link #format}.
     */
    public HlsUrl(String url, Format format, int firstLineNum, int lastLineNum) {
      this.url = url;
      this.format = format;
      this.firstLineNum = firstLineNum;
      this.lastLineNum = lastLineNum;
    }
  }

  /**
   * The list of variants declared by the playlist.
   */
  public final List<HlsUrl> variants;
  /**
   * The list of demuxed audios declared by the playlist.
   */
  public final List<HlsUrl> audios;
  /**
   * The list of subtitles declared by the playlist.
   */
  public final List<HlsUrl> subtitles;

  /**
   * The format of the audio muxed in the variants. May be null if the playlist does not declare any
   * muxed audio.
   */
  private final Format muxedAudioFormat;
  /**
   * The format of the closed captions declared by the playlist. May be empty if the playlist
   * explicitly declares no captions are available, or null if the playlist does not declare any
   * captions information.
   */
  private final List<Format> muxedCaptionFormats;

  /**
   * @param baseUri See {@link #baseUri}.
   * @param tags See {@link #tags}.
   * @param variants See {@link #variants}.
   * @param audios See {@link #audios}.
   * @param subtitles See {@link #subtitles}.
   * @param muxedAudioFormat See {@link #muxedAudioFormat}.
   * @param muxedCaptionFormats See {@link #muxedCaptionFormats}.
   */
  public HlsMasterPlaylist(String baseUri, List<String> tags, List<HlsUrl> variants,
      List<HlsUrl> audios, List<HlsUrl> subtitles, Format muxedAudioFormat,
      List<Format> muxedCaptionFormats) {
    super(baseUri, tags);
    this.variants = Collections.unmodifiableList(variants);
    this.audios = Collections.unmodifiableList(audios);
    this.subtitles = Collections.unmodifiableList(subtitles);
    this.muxedAudioFormat = muxedAudioFormat;
    this.muxedCaptionFormats = muxedCaptionFormats != null
        ? Collections.unmodifiableList(muxedCaptionFormats) : null;
  }

  private static List<HlsUrl> copyRenditionsList(
      List<HlsUrl> renditions, int renditionType, List<RenditionKey> renditionKeys) {
    List<HlsUrl> copiedRenditions = new ArrayList<>(renditionKeys.size());
    for (int i = 0; i < renditions.size(); i++) {
      HlsUrl rendition = renditions.get(i);
      for (int j = 0; j < renditionKeys.size(); j++) {
        RenditionKey renditionKey = renditionKeys.get(j);
        if (renditionKey.type == renditionType && renditionKey.trackIndex == i) {
          copiedRenditions.add(rendition);
          break;
        }
      }
    }
    return copiedRenditions;
  }

}
