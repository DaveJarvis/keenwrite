/* Copyright 2020-2021 White Magic Software, Ltd. -- All rights reserved. */
package com.keenwrite.ui.controls;

import com.keenwrite.events.StatusEvent;
import org.controlsfx.control.StatusBar;
import org.greenrobot.eventbus.Subscribe;

import static com.keenwrite.events.Bus.register;
import static javafx.application.Platform.isFxApplicationThread;
import static javafx.application.Platform.runLater;

/**
 * Responsible for handling application status events.
 */
public class EventedStatusBar extends StatusBar {
  public EventedStatusBar() {
    register( this );
  }

  /**
   * Called when an application problem is encountered. Updates the status
   * bar to show the first line of the given message. This method is
   * idempotent (if the message text is already set to the text from the
   * given message, no update is performed).
   *
   * @param event The event containing information about the problem.
   */
  @Subscribe
  public void handle( final StatusEvent event ) {
    final var m = event.getMessage() + event.getException();

    // Don't burden the repaint thread if there's no status bar change.
    if( !getText().equals( m ) ) {
      final var i = m.indexOf( '\n' );

      final Runnable update =
        () -> setText( m.substring( 0, i > 0 ? i : m.length() ) );

      if( isFxApplicationThread() ) {
        update.run();
      }
      else {
        runLater( update );
      }
    }
  }
}
