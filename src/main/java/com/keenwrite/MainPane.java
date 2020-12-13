/* Copyright 2020 White Magic Software, Ltd. -- All rights reserved. */
package com.keenwrite;

import com.keenwrite.editors.TextDefinition;
import com.keenwrite.editors.TextEditor;
import com.keenwrite.editors.TextResource;
import com.keenwrite.editors.base.PlainTextEditor;
import com.keenwrite.editors.definition.DefinitionEditor;
import com.keenwrite.editors.definition.DefinitionTabSceneFactory;
import com.keenwrite.editors.definition.yaml.YamlTreeTransformer;
import com.keenwrite.editors.markdown.MarkdownEditor;
import com.keenwrite.io.File;
import com.keenwrite.io.MediaType;
import com.keenwrite.preferences.Workspace;
import com.keenwrite.preview.HtmlPreview;
import com.keenwrite.processors.IdentityProcessor;
import com.keenwrite.processors.Processor;
import com.keenwrite.processors.ProcessorContext;
import com.keenwrite.processors.ProcessorFactory;
import com.keenwrite.processors.markdown.Caret;
import com.keenwrite.processors.markdown.CaretExtension;
import com.keenwrite.service.events.Notifier;
import com.panemu.tiwulfx.control.dock.DetachableTab;
import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem.TreeModificationEvent;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.keenwrite.Constants.*;
import static com.keenwrite.ExportFormat.NONE;
import static com.keenwrite.Messages.get;
import static com.keenwrite.StatusBarNotifier.clue;
import static com.keenwrite.editors.definition.MapInterpolator.interpolate;
import static com.keenwrite.io.MediaType.*;
import static com.keenwrite.preferences.Workspace.KEY_UI_FILES_PATH;
import static com.keenwrite.processors.ProcessorFactory.createProcessors;
import static com.keenwrite.service.events.Notifier.NO;
import static com.keenwrite.service.events.Notifier.YES;
import static javafx.application.Platform.runLater;
import static javafx.scene.control.TabPane.TabClosingPolicy.ALL_TABS;
import static javafx.scene.input.KeyCode.SPACE;
import static javafx.scene.input.KeyCombination.CONTROL_DOWN;
import static javafx.util.Duration.millis;
import static org.fxmisc.wellbehaved.event.EventPattern.keyPressed;

/**
 * Responsible for wiring together the main application components for a
 * particular workspace (project). These include the definition views,
 * text editors, and preview pane along with any corresponding controllers.
 */
public final class MainPane extends SplitPane {
  private static final Notifier sNotifier = Services.load( Notifier.class );
  /**
   * Prevents re-instantiation of processing classes.
   */
  private final Map<TextResource, Processor<String>> mProcessors =
      new HashMap<>();

  /**
   * Groups similar file type tabs together.
   */
  private final Map<MediaType, DetachableTabPane> mTabPanes = new HashMap<>();

  /**
   * Stores definition names and values.
   */
  private final Map<String, String> mResolvedMap =
      new HashMap<>( DEFAULT_MAP_SIZE );

  /**
   * Renders the actively selected plain text editor tab.
   */
  private final HtmlPreview mHtmlPreview = new HtmlPreview();

  /**
   * Changing the active editor fires the value changed event. This allows
   * refreshes to happen when external definitions are modified and need to
   * trigger the processing chain.
   */
  private final ObjectProperty<TextEditor> mActiveTextEditor =
      createActiveTextEditor();

  /**
   * Changing the active definition editor fires the value changed event. This
   * allows refreshes to happen when external definitions are modified and need
   * to trigger the processing chain.
   */
  private final ObjectProperty<TextDefinition> mActiveDefinitionEditor =
      createActiveDefinitionEditor( mActiveTextEditor );

  /**
   * Responsible for creating a new scene when a tab is detached into
   * its own window frame.
   */
  private final DefinitionTabSceneFactory mDefinitionTabSceneFactory =
      createDefinitionTabSceneFactory( mActiveDefinitionEditor );

  /**
   * Tracks the number of detached tab panels opened into their own windows,
   * which allows unique identification of subordinate windows by their title.
   * It is doubtful more than 128 windows, much less 256, will be created.
   */
  private byte mWindowCount;

  /**
   * Called when the definition data is changed.
   */
  private final EventHandler<TreeModificationEvent<Event>> mTreeHandler =
      event -> {
        final var editor = mActiveDefinitionEditor.get();

        resolve( editor );
        process( getActiveTextEditor() );
        save( editor );
      };

  /**
   * Adds all content panels to the main user interface. This will load the
   * configuration settings from the workspace to reproduce the settings from
   * a previous session.
   */
  public MainPane() {
    open( bin( getWorkspace().getListFiles( KEY_UI_FILES_PATH ) ) );

    final var tabPane = obtainDetachableTabPane( TEXT_HTML );
    tabPane.addTab( "HTML", mHtmlPreview );
    addTabPane( tabPane );

    final var ratio = 100f / getItems().size() / 100;
    final var positions = getDividerPositions();

    for( int i = 0; i < positions.length; i++ ) {
      positions[ i ] = ratio * i;
    }

    // TODO: Load divider positions from exported settings, see bin() comment.
    setDividerPositions( positions );

    // Once the main scene's window regains focus, update the active definition
    // editor to the currently selected tab.
    runLater(
        () -> getWindow().focusedProperty().addListener( ( c, o, n ) -> {
          if( n != null && n ) {
            final var pane = mTabPanes.get( TEXT_YAML );
            final var model = pane.getSelectionModel();
            final var tab = model.getSelectedItem();

            if( tab != null ) {
              final var editor = (TextDefinition) tab.getContent();

              mActiveDefinitionEditor.set( editor );
            }
          }
        } )
    );

    forceRepaint();
  }

  /**
   * Force preview pane refresh on Windows.
   */
  private void forceRepaint() {
//    if( IS_OS_WINDOWS ) {
//      splitPane.getDividers().get( 1 ).positionProperty().addListener(
//          ( l, oValue, nValue ) -> runLater(
//              () -> getHtmlPreview().repaintScrollPane()
//          )
//      );
//    }
  }

  /**
   * Opens all the files into the application, provided the paths are unique.
   * This may only be called for any type of files that a user can edit
   * (i.e., update and persist), such as definitions and text files.
   *
   * @param files The list of files to open.
   */
  public void open( final List<File> files ) {
    files.forEach( this::open );
  }

  /**
   * This opens the given file. Since the preview pane is not a file that
   * can be opened, it is safe to add a listener to the detachable pane.
   *
   * @param file The file to open.
   */
  private void open( final File file ) {
    final var mediaType = file.getMediaType();
    final var tab = createTab( file );
    final var node = tab.getContent();
    final var tabPane = obtainDetachableTabPane( mediaType );
    final var newTabPane = !getItems().contains( tabPane );

    tab.setTooltip( createTooltip( file ) );
    tabPane.setFocusTraversable( false );
    tabPane.setTabClosingPolicy( ALL_TABS );
    tabPane.getTabs().add( tab );

    if( newTabPane ) {
      var index = getItems().size();

      if( node instanceof TextDefinition ) {
        tabPane.setSceneFactory( mDefinitionTabSceneFactory::create );
        index = 0;
      }

      addTabPane( index, tabPane );
    }

    getWorkspace().putListItem( KEY_UI_FILES_PATH, file );
  }

  /**
   * Opens a new text editor document using the default document file name.
   */
  public void newTextEditor() {
    open( DEFAULT_DOCUMENT );
  }

  /**
   * Opens a new definition editor document using the default definition
   * file name.
   */
  public void newDefinitionEditor() {
    open( DEFAULT_DEFINITION );
  }

  /**
   * Iterates over all tab panes to find all {@link TextEditor}s and request
   * that they save themselves.
   */
  public void saveAll() {
    mTabPanes.forEach(
        ( mt, tp ) -> tp.getTabs().forEach( ( tab ) -> {
          final var node = tab.getContent();
          if( node instanceof TextEditor ) {
            save( ((TextEditor) node) );
          }
        } )
    );
  }

  /**
   * Requests that the active {@link TextEditor} saves itself. Don't bother
   * checking if modified first because if the user swaps external media from
   * an external source (e.g., USB thumb drive), save should not second-guess
   * the user: save always re-saves. Also, it's less code.
   */
  public void save() {
    save( getActiveTextEditor() );
  }

  /**
   * Saves the active {@link TextEditor} under a new name.
   *
   * @param file The new active editor {@link File} reference.
   */
  public void saveAs( final File file ) {
    assert file != null;
    final var editor = getActiveTextEditor();
    final var tab = getTab( editor );

    editor.rename( file );
    tab.ifPresent( t -> {
      t.setText( editor.getFilename() );
      t.setTooltip( createTooltip( file ) );
    } );

    save();
  }

  /**
   * Saves the given {@link TextResource} to a file. This is typically used
   * to save either an instance of {@link TextEditor} or {@link TextDefinition}.
   *
   * @param resource The resource to export.
   */
  private void save( final TextResource resource ) {
    try {
      resource.save();
    } catch( final Exception ex ) {
      clue( ex );
      sNotifier.alert(
          getWindow(), resource.getPath(), "TextResource.saveFailed", ex
      );
    }
  }

  /**
   * Closes all open {@link TextEditor}s; all {@link TextDefinition}s stay open.
   *
   * @return {@code true} when all editors, modified or otherwise, were
   * permitted to close; {@code false} when one or more editors were modified
   * and the user requested no closing.
   */
  public boolean closeAll() {
    var closable = true;

    for( final var entry : mTabPanes.entrySet() ) {
      final var tabPane = entry.getValue();
      final var tabIterator = tabPane.getTabs().iterator();

      while( tabIterator.hasNext() ) {
        final var tab = tabIterator.next();
        final var node = tab.getContent();

        if( node instanceof TextEditor &&
            (closable &= canClose( (TextEditor) node )) ) {
          tabIterator.remove();
          close( tab );
        }
      }
    }

    return closable;
  }

  /**
   * Calls the tab's {@link Tab#getOnClosed()} handler to carry out a close
   * event.
   *
   * @param tab The {@link Tab} that was closed.
   */
  private void close( final Tab tab ) {
    final var handler = tab.getOnClosed();

    if( handler != null ) {
      handler.handle( new ActionEvent() );
    }
  }

  /**
   * Closes the active tab; delegates to {@link #canClose(TextEditor)}.
   */
  public void close() {
    final var editor = getActiveTextEditor();
    if( canClose( editor ) ) {
      close( editor );
    }
  }

  /**
   * Closes the given {@link TextEditor}. This must not be called from within
   * a loop that iterates over the tab panes using {@code forEach}, lest a
   * concurrent modification exception be thrown.
   *
   * @param editor The {@link TextEditor} to close, without confirming with
   *               the user.
   */
  private void close( final TextEditor editor ) {
    getTab( editor ).ifPresent(
        ( tab ) -> {
          tab.getTabPane().getTabs().remove( tab );
          close( tab );
        }
    );
  }

  /**
   * Answers whether the given {@link TextEditor} may be closed.
   *
   * @param editor The {@link TextEditor} to try closing.
   * @return {@code true} when the editor may be closed; {@code false} when
   * the user has requested to keep the editor open.
   */
  private boolean canClose( final TextEditor editor ) {
    final var editorTab = getTab( editor );
    final var canClose = new AtomicBoolean( true );

    if( editor.isModified() ) {
      final var filename = new StringBuilder();
      editorTab.ifPresent( ( tab ) -> filename.append( tab.getText() ) );

      final var message = sNotifier.createNotification(
          Messages.get( "Alert.file.close.title" ),
          Messages.get( "Alert.file.close.text" ),
          filename.toString()
      );

      final var dialog = sNotifier.createConfirmation( getWindow(), message );

      dialog.showAndWait().ifPresent(
          save -> canClose.set( save == YES ? editor.save() : save == NO )
      );
    }

    return canClose.get();
  }

  private ObjectProperty<TextEditor> createActiveTextEditor() {
    final var editor = new SimpleObjectProperty<TextEditor>();

    editor.addListener( ( c, o, n ) -> {
      if( n != null ) {
        mHtmlPreview.setBaseUri( n.getPath() );
        process( n );
      }
    } );

    return editor;
  }

  /**
   * Returns the tab that contains the given {@link TextEditor}.
   *
   * @param editor The {@link TextEditor} instance to find amongst the tabs.
   * @return The first tab having content that matches the given tab.
   */
  private Optional<Tab> getTab( final TextEditor editor ) {
    return mTabPanes.values()
                    .stream()
                    .flatMap( pane -> pane.getTabs().stream() )
                    .filter( tab -> editor.equals( tab.getContent() ) )
                    .findFirst();
  }

  /**
   * Creates a new {@link DefinitionEditor} wrapped in a listener that
   * is used to detect when the active {@link DefinitionEditor} has changed.
   * Upon changing, the {@link #mResolvedMap} is updated and the active
   * text editor is refreshed.
   *
   * @param editor Text editor to update with the revised resolved map.
   * @return A newly configured property that represents the active
   * {@link DefinitionEditor}, never null.
   */
  private ObjectProperty<TextDefinition> createActiveDefinitionEditor(
      final ObjectProperty<TextEditor> editor ) {
    final var definitions = new SimpleObjectProperty<TextDefinition>();
    definitions.addListener( ( c, o, n ) -> {
      resolve( n == null ? createDefinitionEditor() : n );
      process( editor.get() );
    } );

    return definitions;
  }

  /**
   * Instantiates a factory that's responsible for creating new scenes when
   * a tab is dropped outside of any application window. The definition tabs
   * are fairly complex in that only one may be active at any time. When
   * activated, the {@link #mResolvedMap} must be updated to reflect the
   * hierarchy displayed in the {@link DefinitionEditor}.
   *
   * @param activeDefinitionEditor The current {@link DefinitionEditor}.
   * @return An object that listens to {@link DefinitionEditor} tab focus
   * changes.
   */
  private DefinitionTabSceneFactory createDefinitionTabSceneFactory(
      final ObjectProperty<TextDefinition> activeDefinitionEditor ) {
    return new DefinitionTabSceneFactory( ( tab ) -> {
      assert tab != null;

      var node = tab.getContent();
      if( node instanceof TextDefinition ) {
        activeDefinitionEditor.set( (DefinitionEditor) node );
      }
    } );
  }

  private DetachableTab createTab( final File file ) {
    final var r = createTextResource( file );
    final var tab = new DetachableTab( r.getFilename(), r.getNode() );

    r.modifiedProperty().addListener(
        ( c, o, n ) -> tab.setText( r.getFilename() + (n ? "*" : "") )
    );

    // This is called when either the tab is closed by the user clicking on
    // the tab's close icon or when closing (all) from the file menu.
    tab.setOnClosed(
        ( __ ) -> getWorkspace().purgeListItem( KEY_UI_FILES_PATH, file )
    );

    return tab;
  }

  /**
   * Creates bins for the different {@link MediaType}s, which eventually are
   * added to the UI as separate tab panes. If ever a general-purpose scene
   * exporter is developed to serialize a scene to an FXML file, this could
   * be replaced by such a class.
   * <p>
   * When binning the files, this makes sure that at least one file exists
   * for every type. If the user has opted to close a particular type (such
   * as the definition pane), the view will suppressed elsewhere.
   * </p>
   * <p>
   * The order that the binned files are returned will be reflected in the
   * order that the corresponding panes are rendered in the UI. Each different
   * {@link MediaType} will be created in its own pane.
   * </p>
   *
   * @param files The files to bin by {@link MediaType}.
   * @return An in-order list of files, first by structured definition files,
   * then by plain text documents.
   */
  private List<File> bin( final List<File> files ) {
    final var map = new HashMap<MediaType, List<File>>();
    map.put( TEXT_YAML, new ArrayList<>() );
    map.put( TEXT_MARKDOWN, new ArrayList<>() );
    map.put( UNDEFINED, new ArrayList<>() );

    for( final var file : files ) {
      final var list = map.computeIfAbsent(
          file.getMediaType(), k -> new ArrayList<>()
      );

      list.add( file );
    }

    final var definitions = map.get( TEXT_YAML );
    final var documents = map.get( TEXT_MARKDOWN );
    final var undefined = map.get( UNDEFINED );

    if( definitions.isEmpty() ) {
      definitions.add( DEFAULT_DEFINITION );
    }

    if( documents.isEmpty() ) {
      documents.add( DEFAULT_DOCUMENT );
    }

    final var result = new ArrayList<File>( files.size() );
    result.addAll( definitions );
    result.addAll( documents );
    result.addAll( undefined );

    return result;
  }

  /**
   * Uses the given {@link TextDefinition} instance to update the
   * {@link #mResolvedMap}.
   *
   * @param editor A non-null, possibly empty definition editor.
   */
  private void resolve( final TextDefinition editor ) {
    assert editor != null;
    mResolvedMap.clear();
    mResolvedMap.putAll( interpolate( new HashMap<>( editor.toMap() ) ) );
  }

  /**
   * Force the active editor to update, which will cause the processor
   * to re-evaluate the interpolated definition map thereby updating the
   * preview pane.
   *
   * @param editor Contains the source document to update in the preview pane.
   */
  private void process( final TextEditor editor ) {
    mProcessors.getOrDefault( editor, IdentityProcessor.INSTANCE )
               .apply( editor == null ? "" : editor.getText() );
    mHtmlPreview.scrollTo( CARET_ID );
  }

  /**
   * Lazily creates a {@link DetachableTabPane} configured to handle focus
   * requests by delegating to the selected tab's content. The tab pane is
   * associated with a given media type so that similar files can be grouped
   * together.
   *
   * @param mediaType The media type to associate with the tab pane.
   * @return An instance of {@link DetachableTabPane} that will handle
   * docking of tabs.
   */
  private DetachableTabPane obtainDetachableTabPane(
      final MediaType mediaType ) {
    return mTabPanes.computeIfAbsent(
        mediaType, ( mt ) -> {
          final var tabPane = new DetachableTabPane();

          // Derive the new title from the main window title.
          tabPane.setStageOwnerFactory( ( stage ) -> {
            final var title = get(
                "Detach.tab.title",
                ((Stage) getWindow()).getTitle(), ++mWindowCount
            );
            stage.setTitle( title );
            return getScene().getWindow();
          } );

          // Multiple tabs can be added simultaneously.
          tabPane.getTabs().addListener(
              ( final ListChangeListener.Change<? extends Tab> listener ) -> {
                while( listener.next() ) {
                  if( listener.wasAdded() ) {
                    final var tabs = listener.getAddedSubList();

                    tabs.forEach( ( tab ) -> {
                      final var node = tab.getContent();

                      if( node instanceof TextEditor ) {
                        initScrollEventListener( tab );
                      }
                    } );

                    // Select the last tab opened.
                    final var index = tabs.size() - 1;
                    if( index >= 0 ) {
                      final var tab = tabs.get( index );
                      tabPane.getSelectionModel().select( tab );
                      tab.getContent().requestFocus();
                    }
                  }
                }
              }
          );

          final var model = tabPane.getSelectionModel();

          model.selectedItemProperty().addListener( ( c, o, n ) -> {
            if( o != null && n == null ) {
              final var node = o.getContent();

              // If the last definition editor in the active pane was closed,
              // clear out the definitions then refresh the text editor.
              if( node instanceof TextDefinition ) {
                mActiveDefinitionEditor.set( createDefinitionEditor() );
              }
            }
            else if( n != null ) {
              final var node = n.getContent();

              if( node instanceof TextEditor ) {
                // Changing the active node will fire an event, which will
                // update the preview panel and grab focus.
                mActiveTextEditor.set( (TextEditor) node );
                runLater( node::requestFocus );
              }
              else if( node instanceof TextDefinition ) {
                mActiveDefinitionEditor.set( (DefinitionEditor) node );
              }
            }
          } );

          return tabPane;
        }
    );
  }

  /**
   * Synchronizes scrollbar positions between the given {@link Tab} that
   * contains an instance of {@link TextEditor} and {@link HtmlPreview} pane.
   *
   * @param tab The container for an instance of {@link TextEditor}.
   */
  private void initScrollEventListener( final Tab tab ) {
    final var editor = (TextEditor) tab.getContent();
    final var scrollPane = editor.getScrollPane();
    final var scrollBar = mHtmlPreview.getVerticalScrollBar();
    final var handler = new ScrollEventHandler( scrollPane, scrollBar );
    handler.enabledProperty().bind( tab.selectedProperty() );
  }

  private void addTabPane( final int index, final DetachableTabPane tabPane ) {
    getItems().add( index, tabPane );
  }

  private void addTabPane( final DetachableTabPane tabPane ) {
    addTabPane( getItems().size(), tabPane );
  }

  /**
   * @param path  Used by {@link ProcessorFactory} to determine
   *              {@link Processor} type to create based on file type.
   * @param caret Used by {@link CaretExtension} to add ID attribute into
   *              preview document for scrollbar synchronization.
   * @return A new {@link ProcessorContext} to use when creating an instance of
   * {@link Processor}.
   */
  private ProcessorContext createProcessorContext(
      final Path path, final Caret caret ) {
    return new ProcessorContext(
        mHtmlPreview, mResolvedMap, path, caret, NONE
    );
  }

  public ProcessorContext createProcessorContext( final TextEditor t ) {
    return createProcessorContext( t.getPath(), t.getCaret() );
  }

  @SuppressWarnings({"RedundantCast", "unchecked", "RedundantSuppression"})
  private TextResource createTextResource( final File file ) {
    final var mediaType = file.getMediaType();

    return switch( mediaType ) {
      case TEXT_MARKDOWN -> createMarkdownEditor( file );
      case TEXT_YAML -> createDefinitionEditor( file );
      default -> new PlainTextEditor( file );
    };
  }

  /**
   * Creates an instance of {@link MarkdownEditor} that listens for both
   * caret change events and text change events. Text change events must
   * take priority over caret change events because it's possible to change
   * the text without moving the caret (e.g., delete selected text).
   *
   * @param file The file containing contents for the text editor.
   * @return A non-null text editor.
   */
  private TextResource createMarkdownEditor( final File file ) {
    final var path = file.toPath();
    final var editor = new MarkdownEditor( file );
    final var caret = editor.getCaret();
    final var context = createProcessorContext( path, caret );

    mProcessors.computeIfAbsent( editor, p -> createProcessors( context ) );

    editor.addDirtyListener( ( c, o, n ) -> {
      if( n ) {
        process( getActiveTextEditor() );
      }
    } );

    editor.addEventListener(
        keyPressed( SPACE, CONTROL_DOWN ), this::autoinsert
    );

    // Set the active editor, which refreshes the preview panel.
    mActiveTextEditor.set( editor );

    return editor;
  }

  /**
   * Delegates to {@link #autoinsert()}.
   *
   * @param event Ignored.
   */
  private void autoinsert( final KeyEvent event ) {
    autoinsert();
  }

  /**
   * Finds a node that matches the word at the caret, then inserts the
   * corresponding definition. The definition token delimiters depend on
   * the type of file being edited.
   */
  public void autoinsert() {
    final var definitions = getActiveTextDefinition();
    final var editor = getActiveTextEditor();
    final var mediaType = editor.getFile().getMediaType();
    final var decorator = getSigilOperator( mediaType );

    DefinitionNameInjector.autoinsert( editor, definitions, decorator );
  }

  private TextDefinition createDefinitionEditor() {
    return createDefinitionEditor( DEFAULT_DEFINITION );
  }

  private TextDefinition createDefinitionEditor( final File file ) {
    final var editor = new DefinitionEditor( file, new YamlTreeTransformer() );

    editor.addTreeChangeHandler( mTreeHandler );

    return editor;
  }

  private Tooltip createTooltip( final File file ) {
    final var path = file.toPath();
    final var tooltip = new Tooltip( path.toString() );

    tooltip.setShowDelay( millis( 200 ) );
    return tooltip;
  }

  public TextEditor getActiveTextEditor() {
    return mActiveTextEditor.get();
  }

  public ReadOnlyObjectProperty<TextEditor> activeTextEditorProperty() {
    return mActiveTextEditor;
  }

  public TextDefinition getActiveTextDefinition() {
    return mActiveDefinitionEditor.get();
  }

  public Window getWindow() {
    return getScene().getWindow();
  }

  private Workspace getWorkspace() {
    return Workspace.getInstance();
  }
}