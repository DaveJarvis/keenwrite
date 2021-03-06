/* Copyright 2020-2021 White Magic Software, Ltd. -- All rights reserved. */
package com.keenwrite.service.events.impl;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.DialogPane;

import static com.keenwrite.constants.Constants.sSettings;
import static javafx.scene.control.ButtonBar.BUTTON_ORDER_WINDOWS;

/**
 * Ensures a consistent button order for alert dialogs across platforms (because
 * the default button order on Linux defies all logic).
 */
public class ButtonOrderPane extends DialogPane {
  public ButtonOrderPane() {
  }

  @Override
  protected Node createButtonBar() {
    final var node = (ButtonBar) super.createButtonBar();
    node.setButtonOrder( getButtonOrder() );
    return node;
  }

  private String getButtonOrder() {
    return sSettings.getSetting(
      "dialog.alert.button.order.windows", BUTTON_ORDER_WINDOWS );
  }
}
