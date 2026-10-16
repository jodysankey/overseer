 /**
  * Compact Plasmoid UI definition intended for use on a toolbar
  *
  * Copyright Jody Sankey 2016
  *
  * This software may be modified and distributed under the terms of the MIT license.
  * See the LICENSE.md file for details.
  */

import QtQml
import QtQuick
import QtQuick.Layouts
import org.kde.plasma.plasmoid
import org.kde.plasma.core as PlasmaCore
import org.kde.kirigami as Kirigami

Item {
    id:root
    property string text: ""
    property string color: "LightBlue"
    signal clicked()

    Layout.minimumWidth: Plasmoid.formFactor != PlasmaCore.Types.Vertical ? root.height : Kirigami.Units.gridUnit
    Layout.minimumHeight: Plasmoid.formFactor == PlasmaCore.Types.Vertical ? root.width : Kirigami.Units.gridUnit

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
            onClicked: root.clicked()
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