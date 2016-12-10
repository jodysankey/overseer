 /**
  * Compact Plasmoid UI definition intended for use on a toolbar
  *
  * Copyright Jody Sankey 2016
  *
  * This software may be modified and distributed under the terms of the MIT license.
  * See the LICENSE.md file for details.
  */

import QtQml 2.2
import QtQuick 2.0
import QtQuick.Controls 1.4
import QtQuick.Layouts 1.1
import org.kde.plasma.plasmoid 2.0
import org.kde.plasma.core 2.0 as PlasmaCore

Item {
    id:root
    property string text: ""
    property string color: "LightBlue"

    Layout.minimumWidth: plasmoid.formFactor != PlasmaCore.Types.Vertical ? root.height : units.gridUnit
    Layout.minimumHeight: plasmoid.formFactor == PlasmaCore.Types.Vertical ? root.width : units.gridUnit

    Rectangle {
        anchors {
            fill: parent
            // Leave space for the expanded selector
            topMargin: 3
            bottomMargin: 3
        }
        color: root.color
        radius: 8
        Rectangle {
            anchors.fill: parent
            radius: 8
            gradient: Gradient {
                GradientStop { position: 0.0; color: "#20FFFFFF" }
                GradientStop { position: 0.4; color: "#00FFFFFF" }
                GradientStop { position: 0.6; color: "#00000000" }
                GradientStop { position: 1.0; color: "#20000000" }
            }
        }
        MouseArea {
            anchors.fill: parent
            onClicked: plasmoid.expanded = !plasmoid.expanded
        }
    }
    Text {
        text: root.text
        horizontalAlignment: Text.AlignHCenter
        verticalAlignment: Text.AlignVCenter
        anchors {
            fill: parent
            margins: 3
        }
        fontSizeMode: Text.Fit
        style: Text.Raised
        styleColor: "Grey"
        renderType: Text.NativeRendering
        font.pixelSize: 100
    }
}