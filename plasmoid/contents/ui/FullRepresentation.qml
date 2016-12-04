 /**
  * KDE Plasma script to monitor Overseer execution status
  *
  * Copyright Jody Sankey 2016
  */

import QtQml 2.2
import QtQuick 2.0
import QtQuick.Controls 1.4
import org.kde.plasma.core 2.0 as PlasmaCore
import org.kde.plasma.components 2.0 as PlasmaComponents
import org.kde.plasma.extras 2.0 as PlasmaExtras

Item {
    id:root
    signal runRequested()
    property string status: ""
    property string lastRun: ""
    property string version: ""
    property string color: "LightBlue"
    property ListModel history

    width: units.gridUnit * 25
    height: units.gridUnit * 14

    // High level status
    Rectangle {
        x: units.gridUnit * 1
        y: units.gridUnit * 1
        width: units.gridUnit * 2
        height: units.gridUnit * 2
        radius: units.gridUnit
        color: root.color
    }
    PlasmaComponents.Label {
        x: units.gridUnit * 4
        y: units.gridUnit * 1
        height: units.gridUnit * 2
        verticalAlignment: Text.AlignVCenter
        font.pointSize: theme.defaultFont.pointSize * 2
        text: root.status
    }

    // Last run time and observed version
    PlasmaComponents.Label {
        x: units.gridUnit * 1
        y: units.gridUnit * 4
        font.italic: true
        opacity: 0.6
        text: "Last Run"
    }
    PlasmaComponents.Label {
        x: units.gridUnit * 1
        y: units.gridUnit * 5
        text: root.lastRun
    }

    PlasmaComponents.Label {
        x: units.gridUnit * 1
        y: units.gridUnit * 7
        font.italic: true
        text: "Overseer"
        opacity: 0.6
    }
    PlasmaComponents.Label {
        x: units.gridUnit * 1
        y: units.gridUnit * 8
        text: "v" + root.version
    }

    // Restart button
    Button {
        anchors {
            left: parent.left
            bottom: parent.bottom
            leftMargin: units.gridUnit
            bottomMargin: units.gridUnit
        }
        width: (parent.width * 0.4) - units.gridUnit * 2
        height: units.gridUnit * 2
        text: "Run now"
        onClicked: root.runRequested()
    }

    // History
    PlasmaExtras.ScrollArea {
        anchors {
            right: parent.right
            bottom: parent.bottom
            top: parent.top
            topMargin: units.gridUnit * 4
            bottomMargin: units.gridUnit
        }
        width: parent.width * 0.6

        ListView {
            focus: true
            boundsBehavior: Flickable.StopAtBounds
            model: root.history
            delegate: Row {
                anchors {
                    left: parent.left
                    right: parent.right
                    leftMargin: units.gridUnit * 0.5
                }
                PlasmaComponents.Label {
                    height: implicitHeight
                    width: units.gridUnit * 7
                    horizontalAlignment: Text.AlignLeft
                    text: model.start
                }
                PlasmaComponents.Label {
                    height: implicitHeight
                    width: units.gridUnit * 4.5
                    horizontalAlignment: Text.AlignRight
                    text: model.duration
                }
                PlasmaComponents.Label {
                    height: implicitHeight
                    width: units.gridUnit * 1.5
                    horizontalAlignment: Text.AlignRight
                    text: model.exitCode
                }
            }
            section {
                property: "command"
                criteria: ViewSection.FullString
                delegate: PlasmaComponents.Label {
                    font.italic: true
                    opacity: 0.6
                    anchors {
                        left: parent.left
                        right: parent.right
                        topMargin: 5
                    }
                    height: implicitHeight
                    elide: Text.ElideRight
                    text: section
                }
            }
        }
    }
    
}