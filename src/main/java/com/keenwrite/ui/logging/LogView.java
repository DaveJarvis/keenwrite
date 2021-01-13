/* Copyright 2020-2021 White Magic Software, Ltd. -- All rights reserved. */
package com.keenwrite.ui.logging;

import com.keenwrite.MainApp;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.keenwrite.Constants.ICON_DIALOG;
import static com.keenwrite.Constants.NEWLINE;
import static com.keenwrite.Messages.get;
import static com.keenwrite.StatusNotifier.clue;
import static java.time.LocalDateTime.now;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Arrays.stream;
import static javafx.collections.FXCollections.observableArrayList;
import static javafx.event.ActionEvent.ACTION;
import static javafx.scene.control.Alert.AlertType.INFORMATION;
import static javafx.scene.control.ButtonType.OK;
import static javafx.scene.control.SelectionMode.MULTIPLE;
import static javafx.scene.input.Clipboard.getSystemClipboard;
import static javafx.scene.input.KeyCode.C;
import static javafx.scene.input.KeyCode.INSERT;
import static javafx.scene.input.KeyCombination.CONTROL_ANY;
import static javafx.stage.Modality.NONE;

/**
 * Responsible for logging application issues to {@link TableView} entries.
 */
public final class LogView extends Alert {
  /**
   * Number of error messages to retain in the {@link TableView}; must be
   * greater than zero.
   */
  private static final int CACHE_SIZE = 150;

  private final ObservableList<LogEntry> mEntries = observableArrayList();
  private final TableView<LogEntry> mTable = new TableView<>( mEntries );

  public LogView() {
    super( INFORMATION );
    setTitle( get( "App.action.view.issues.text" ) );
    initModality( NONE );
    initTableView();
    setResizable( true );
    initButtons();
    initIcon();
    initActions();
  }

  /**
   * Brings the dialog to the foreground, showing it if needed.
   */
  public void view() {
    super.show();
    getStage().toFront();
  }

  /**
   * Removes all the entries from the list.
   */
  public void clear() {
    mEntries.clear();
    clue();
  }

  public void log( final String message ) {
    log( new LogEntry( message ) );
  }

  public void log( final Throwable error ) {
    log( new LogEntry( error ) );
  }

  public void log( final String message, final Throwable trace ) {
    log( new LogEntry( message, trace ) );
  }

  private void log( final LogEntry logEntry ) {
    // Exit early if the log already contains the message. The status bar will
    // remain current.
    if( mEntries.contains( logEntry ) ) {
      return;
    }

    mEntries.add( logEntry );

    while( mEntries.size() > CACHE_SIZE ) {
      mEntries.remove( 0 );
    }

    mTable.scrollTo( logEntry );
  }

  private void initTableView() {
    final var ctrlC = new KeyCodeCombination( C, CONTROL_ANY );
    final var ctrlInsert = new KeyCodeCombination( INSERT, CONTROL_ANY );

    final var colDate = new TableColumn<LogEntry, String>( "Date" );
    final var colMessage = new TableColumn<LogEntry, String>( "Message" );
    final var colTrace = new TableColumn<LogEntry, String>( "Trace" );

    colDate.setCellValueFactory( log -> log.getValue().dateProperty() );
    colMessage.setCellValueFactory( log -> log.getValue().messageProperty() );
    colTrace.setCellValueFactory( log -> log.getValue().traceProperty() );

    final var columns = mTable.getColumns();
    columns.add( colDate );
    columns.add( colMessage );
    columns.add( colTrace );

    mTable.setMaxWidth( Double.MAX_VALUE );
    mTable.setPrefWidth( 1024 );
    mTable.getSelectionModel().setSelectionMode( MULTIPLE );
    mTable.setOnKeyPressed( event -> {
      if( ctrlC.match( event ) || ctrlInsert.match( event ) ) {
        copyToClipboard( mTable );
      }
    } );

    final var pane = getDialogPane();
    pane.setContent( mTable );
  }

  private void initButtons() {
    final var pane = getDialogPane();
    final var CLEAR = new ButtonType( "CLEAR" );
    pane.getButtonTypes().add( CLEAR );

    final var buttonOk = (Button) pane.lookupButton( OK );
    final var buttonClear = (Button) pane.lookupButton( CLEAR );

    buttonOk.setDefaultButton( true );
    buttonClear.addEventFilter( ACTION, event -> {
      clear();
      event.consume();
    } );

    pane.setOnKeyReleased( t -> {
      switch( t.getCode() ) {
        case ENTER, ESCAPE -> buttonOk.fire();
      }
    } );
  }

  private void initIcon() {
    final var stage = getStage();
    stage.getIcons().add( ICON_DIALOG );
  }

  private void initActions() {
    final var stage = getStage();
    stage.setOnCloseRequest( event -> stage.hide() );
  }

  private Stage getStage() {
    return (Stage) getDialogPane().getScene().getWindow();
  }

  private static final class LogEntry {
    private final StringProperty mDate;
    private final StringProperty mMessage;
    private final StringProperty mTrace;

    /**
     * Constructs a new {@link LogEntry} for the current time, and having
     * no associated stack trace.
     *
     * @param message The error message.
     */
    public LogEntry( final String message ) {
      this( message, null );
    }

    /**
     * Constructs a new {@link LogEntry} for the current time, and using
     * the given error's message.
     *
     * @param error The stack trace, must not be {@code null}.
     */
    public LogEntry( final Throwable error ) {
      this( error.getMessage(), error );
    }

    /**
     * Constructs a new {@link LogEntry} with the current date and time.
     *
     * @param message The error message.
     * @param trace   The stack trace associated with the message, may be
     *                {@code null}.
     */
    public LogEntry( final String message, final Throwable trace ) {
      mDate = new SimpleStringProperty( toString( now() ) );
      mMessage = new SimpleStringProperty( message );
      mTrace = new SimpleStringProperty( toString( trace ) );
    }

    private StringProperty messageProperty() {
      return mMessage;
    }

    private StringProperty dateProperty() {
      return mDate;
    }

    private StringProperty traceProperty() {
      return mTrace;
    }

    private String toString( final LocalDateTime date ) {
      return date.format( ofPattern( "d MMM u HH:mm:ss" ) );
    }

    private String toString( final Throwable trace ) {
      final var sb = new StringBuilder( 256 );

      if( trace != null ) {
        sb.append( trace.getMessage().trim() ).append( NEWLINE );
        stream( trace.getStackTrace() )
          .takeWhile( LogView::filter )
          .limit( 10 )
          .collect( Collectors.toList() )
          .forEach( e -> sb.append( e.toString() ).append( NEWLINE ) );
      }

      return sb.toString();
    }

    @Override
    public boolean equals( final Object o ) {
      if( this == o ) { return true; }
      if( o == null || getClass() != o.getClass() ) { return false; }

      return Objects.equals( mMessage.get(), ((LogEntry) o).mMessage.get() );
    }

    @Override
    public int hashCode() {
      return mMessage != null ? mMessage.hashCode() : 0;
    }
  }

  private static boolean filter( final StackTraceElement e ) {
    final var clazz = e.getClassName();
    return clazz.startsWith( MainApp.class.getPackageName() ) ||
      clazz.startsWith( "org.renjin" );
  }

  /**
   * Copies the contents of the selected rows into the clipboard; code is from
   * <a href="https://stackoverflow.com/a/48126059/59087">StackOverflow</a>.
   *
   * @param table The {@link TableView} having selected rows to copy.
   */
  public void copyToClipboard( final TableView<?> table ) {
    final var sb = new StringBuilder();
    final var rows = new TreeSet<Integer>();
    boolean firstRow = true;

    for( final var position : table.getSelectionModel().getSelectedCells() ) {
      rows.add( position.getRow() );
    }

    for( final var row : rows ) {
      if( !firstRow ) {
        sb.append( '\n' );
      }

      firstRow = false;
      boolean firstCol = true;

      for( final var column : table.getColumns() ) {
        if( !firstCol ) {
          sb.append( '\t' );
        }

        firstCol = false;
        final var data = column.getCellData( row );
        sb.append( data == null ? "" : data.toString() );
      }
    }

    final var contents = new ClipboardContent();
    contents.putString( sb.toString() );
    getSystemClipboard().setContent( contents );
  }
}
