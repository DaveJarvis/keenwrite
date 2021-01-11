/* Copyright 2020-2021 White Magic Software, Ltd. -- All rights reserved. */
package com.keenwrite;

import com.keenwrite.service.Settings;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.keenwrite.Bootstrap.APP_TITLE_LOWERCASE;
import static com.keenwrite.preferences.LocaleScripts.withScript;
import static java.io.File.separator;
import static java.lang.String.format;
import static java.lang.System.getProperty;

/**
 * Defines application-wide default values.
 */
public final class Constants {

  /**
   * Used by the default settings to load the {@link Settings} service. This
   * must come before any attempt is made to create a {@link Settings} object.
   * The reference to {@link Bootstrap#APP_TITLE_LOWERCASE} should cause the
   * JVM to load {@link Bootstrap} prior to proceeding. Loading that class
   * beforehand will read the bootstrap properties file to determine the
   * application name, which is then used to locate the settings properties.
   */
  public static final String PATH_PROPERTIES_SETTINGS =
    format( "/com/%s/settings.properties", APP_TITLE_LOWERCASE );

  /**
   * The {@link Settings} uses {@link #PATH_PROPERTIES_SETTINGS}.
   */
  public static final Settings sSettings = Services.load( Settings.class );

  public static final double WINDOW_X_DEFAULT = 0;
  public static final double WINDOW_Y_DEFAULT = 0;
  public static final double WINDOW_W_DEFAULT = 1200;
  public static final double WINDOW_H_DEFAULT = 800;

  public static final File DOCUMENT_DEFAULT = getFile( "document" );
  public static final File DEFINITION_DEFAULT = getFile( "definition" );

  public static final String APP_BUNDLE_NAME = get( "application.messages" );

  /**
   * Prevent double events when updating files on Linux (save and timestamp).
   */
  public static final int APP_WATCHDOG_TIMEOUT = get(
    "application.watchdog.timeout", 200 );

  public static final String STYLESHEET_MARKDOWN = get(
    "file.stylesheet.markdown" );
  public static final String STYLESHEET_MARKDOWN_LOCALE =
    "file.stylesheet.markdown.locale";
  public static final String STYLESHEET_PREVIEW = get(
    "file.stylesheet.preview" );
  public static final String STYLESHEET_PREVIEW_LOCALE =
    "file.stylesheet.preview.locale";
  public static final String STYLESHEET_SCENE = get( "file.stylesheet.scene" );

  public static final List<Image> LOGOS = createImages(
    "file.logo.16",
    "file.logo.32",
    "file.logo.128",
    "file.logo.256",
    "file.logo.512"
  );

  public static final Image ICON_DIALOG = LOGOS.get( 1 );
  public static final ImageView ICON_DIALOG_NODE = new ImageView( ICON_DIALOG );

  public static final String FILE_PREFERENCES = getPreferencesFilename();

  /**
   * Refer to file name extension settings in the configuration file. Do not
   * terminate with a period.
   */
  public static final String GLOB_PREFIX_FILE = "file.ext";

  /**
   * Three parameters: line number, column number, and offset.
   */
  public static final String STATUS_BAR_LINE = "Main.status.line";

  public static final String STATUS_BAR_OK = "Main.status.state.default";

  /**
   * Used to show an error while parsing, usually syntactical.
   */
  public static final String STATUS_PARSE_ERROR = "Main.status.error.parse";
  public static final String STATUS_DEFINITION_BLANK =
    "Main.status.error.def.blank";
  public static final String STATUS_DEFINITION_EMPTY =
    "Main.status.error.def.empty";

  /**
   * One parameter: the word under the cursor that could not be found.
   */
  public static final String STATUS_DEFINITION_MISSING =
    "Main.status.error.def.missing";

  /**
   * Used when creating flat maps relating to resolved variables.
   */
  public static final int MAP_SIZE_DEFAULT = 128;

  /**
   * Default image extension order to use when scanning.
   */
  public static final String PERSIST_IMAGES_DEFAULT =
    get( "file.ext.image.order" );

  /**
   * Default working directory to use for R startup script.
   */
  public static final File USER_DIRECTORY =
    new File( System.getProperty( "user.dir" ) );

  public static final String NEWLINE = System.lineSeparator();

  /**
   * Default path to use for an untitled (pathless) file.
   */
  public static final Path DEFAULT_DIRECTORY = USER_DIRECTORY.toPath();

  /**
   * Default character set to use when reading/writing files.
   */
  public static final Charset DEFAULT_CHARSET = Charset.defaultCharset();

  /**
   * Default starting delimiter for definition variables. This value must
   * not overlap math delimiters, so do not use $ tokens as the first
   * delimiter.
   */
  public static final String DEF_DELIM_BEGAN_DEFAULT = "{{";

  /**
   * Default ending delimiter for definition variables.
   */
  public static final String DEF_DELIM_ENDED_DEFAULT = "}}";

  /**
   * Default starting delimiter when inserting R variables.
   */
  public static final String R_DELIM_BEGAN_DEFAULT = "x( ";

  /**
   * Default ending delimiter when inserting R variables.
   */
  public static final String R_DELIM_ENDED_DEFAULT = " )";

  /**
   * Resource directory where different language lexicons are located.
   */
  public static final String LEXICONS_DIRECTORY = "lexicons";

  /**
   * Absolute location of true type font files within the Java archive file.
   */
  public static final String FONT_DIRECTORY = "/fonts";

  /**
   * Default text editor font name.
   */
  public static final String FONT_NAME_EDITOR_DEFAULT = "Noto Sans Regular";

  /**
   * Default text editor font size, in points.
   */
  public static final float FONT_SIZE_EDITOR_DEFAULT = 12f;

  /**
   * Default preview font name.
   */
  public static final String FONT_NAME_PREVIEW_DEFAULT = "Source Serif Pro";

  /**
   * Default preview font size, in points.
   */
  public static final float FONT_SIZE_PREVIEW_DEFAULT = 13f;

  /**
   * Default monospace preview font name.
   */
  public static final String FONT_NAME_PREVIEW_MONO_NAME_DEFAULT = "Source Code Pro";

  /**
   * Default monospace preview font size, in points.
   */
  public static final float FONT_SIZE_PREVIEW_MONO_SIZE_DEFAULT = 13f;

  /**
   * Default locale for font loading, including ISO 15924 alpha-4 script code.
   */
  public static final Locale LOCALE_DEFAULT = withScript( Locale.getDefault() );

  /**
   * Default identifier to use for synchronized scrolling.
   */
  public static final String CARET_ID = "caret";

  /**
   * Prevent instantiation.
   */
  private Constants() {
  }

  private static String get( final String key ) {
    return sSettings.getSetting( key, "" );
  }

  @SuppressWarnings( "SameParameterValue" )
  private static int get( final String key, final int defaultValue ) {
    return sSettings.getSetting( key, defaultValue );
  }

  /**
   * Returns a default {@link File} instance based on the given key suffix.
   *
   * @param suffix Appended to {@code "file.default."}.
   * @return A new {@link File} instance that references the settings file name.
   */
  private static File getFile( final String suffix ) {
    return new File( get( "file.default." + suffix ) );
  }

  /**
   * Returns the equivalent of {@code $HOME/.filename.xml}.
   */
  private static String getPreferencesFilename() {
    return format(
      "%s%s.%s.xml",
      getProperty( "user.home" ),
      separator,
      APP_TITLE_LOWERCASE
    );
  }

  /**
   * Converts the given file names to images, such as application icons.
   *
   * @param keys The file names to convert to images.
   * @return The images loaded from the file name references.
   */
  private static List<Image> createImages( final String... keys ) {
    final List<Image> images = new ArrayList<>( keys.length );

    for( final var key : keys ) {
      images.add( new Image( get( key ) ) );
    }

    return images;
  }
}
