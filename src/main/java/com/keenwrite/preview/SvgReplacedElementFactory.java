/* Copyright 2020-2021 White Magic Software, Ltd. -- All rights reserved. */
package com.keenwrite.preview;

import com.keenwrite.io.MediaType;
import com.keenwrite.ui.adapters.ReplacedElementAdapter;
import org.xhtmlrenderer.extend.ReplacedElement;
import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.swing.ImageReplacedElement;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Path;

import static com.keenwrite.events.StatusEvent.clue;
import static com.keenwrite.io.HttpFacade.httpGet;
import static com.keenwrite.preview.MathRenderer.MATH_RENDERER;
import static com.keenwrite.preview.SvgRasterizer.BROKEN_IMAGE_PLACEHOLDER;
import static com.keenwrite.preview.SvgRasterizer.rasterize;
import static com.keenwrite.processors.markdown.extensions.tex.TexNode.HTML_TEX;
import static com.keenwrite.util.ProtocolScheme.getProtocol;

/**
 * Responsible for running {@link SvgRasterizer} on SVG images detected within
 * a document to transform them into rasterized versions.
 */
public final class SvgReplacedElementFactory extends ReplacedElementAdapter {

  public static final String HTML_IMAGE = "img";
  public static final String HTML_IMAGE_SRC = "src";

  private static final ImageReplacedElement BROKEN_IMAGE =
    createImageReplacedElement( BROKEN_IMAGE_PLACEHOLDER );

  @Override
  public ReplacedElement createReplacedElement(
    final LayoutContext c,
    final BlockBox box,
    final UserAgentCallback uac,
    final int cssWidth,
    final int cssHeight ) {
    final var e = box.getElement();

    ImageReplacedElement image = null;

    try {
      BufferedImage raster = null;

      switch( e.getNodeName() ) {
        case HTML_IMAGE -> {
          final var source = e.getAttribute( HTML_IMAGE_SRC );
          var mediaType = MediaType.fromFilename( source );
          URI uri = null;

          if( getProtocol( source ).isHttp() ) {
            if( mediaType.isSvg() || mediaType.isUndefined() ) {
              uri = new URI( source );

              try( final var response = httpGet( uri ) ) {
                mediaType = response.getMediaType();
              }

              // Attempt to rasterize SVG depending on URL resource content.
              if( !mediaType.isSvg() ) {
                uri = null;
              }
            }
          }
          else if( mediaType.isSvg() ) {
            // Attempt to rasterize based on file name.
            final var path = Path.of( new URI( source ).getPath() );

            if( path.isAbsolute() ) {
              uri = path.toUri();
            }
            else {
              final var base = new URI( e.getBaseURI() ).getPath();
              uri = Path.of( base, source ).toUri();
            }
          }

          if( uri != null ) {
            raster = rasterize( uri, box.getContentWidth() );
          }
        }
        case HTML_TEX ->
          // Convert the TeX element to a raster graphic.
          raster = rasterize( MATH_RENDERER.render( e.getTextContent() ) );
      }

      if( raster != null ) {
        image = createImageReplacedElement( raster );
      }
    } catch( final Exception ex ) {
      image = BROKEN_IMAGE;
      clue( ex );
    }

    return image;
  }

  private static ImageReplacedElement createImageReplacedElement(
    final BufferedImage bi ) {
    return new ImageReplacedElement( bi, bi.getWidth(), bi.getHeight() );
  }
}
