 /**
  * KDE Plasma script to monitor Overseer execution status
  *
  * Copyright Jody Sankey 2016
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
        }
        color: root.color
        radius: 8
        Text {
            text: root.text
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            anchors {
                fill: parent
                leftMargin: 3
                rightMargin: 3
                topMargin: 1
                bottomMargin: 1
            }
            fontSizeMode: Text.Fit
            style: Text.Raised
            styleColor: "Grey"
            renderType: Text.NativeRendering
            font.pixelSize: 100
        }
        MouseArea {
            anchors.fill: parent
            onClicked: plasmoid.expanded = !plasmoid.expanded
        }
    }
}