 /**
  * KDE Plasma script to monitor Overseer execution status
  *
  * Copyright Jody Sankey 2016
  */

import QtQml 2.2
import QtQuick 2.0
import QtQuick.Controls 1.4

Item {
    id:root
    signal runRequested()
    property string text: ""
    property string status: ""
    property string runTime: ""
    property string color: "LightBlue"
    property bool connected: false

    Rectangle {
        anchors.fill: parent
        color: root.color
        Text {
            text: root.text
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            anchors.fill: parent
            fontSizeMode: Text.Fit
            font.pixelSize: 100
        }
        MouseArea {
            anchors.fill: parent
            acceptedButtons: Qt.RightButton
            onClicked: contextMenu.popup()
        }
    }

    Menu {
        id: contextMenu
        title: "Basics"
        MenuItem {
            text: root.status
            enabled: false
        }
        MenuItem {
            text: "Last run:  " + root.runTime
            enabled: false
        }
        MenuItem {
            text: "Run now"
            enabled: root.connected
            onTriggered: root.runRequested()
        }
    }
}