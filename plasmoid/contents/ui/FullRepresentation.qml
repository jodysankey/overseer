 /**
  * Expanded Plasmoid UI definition
  *
  * Copyright Jody Sankey 2016
  *
  * This software may be modified and distributed under the terms of the MIT license.
  * See the LICENSE.md file for details.
  */
import QtQml
import QtQuick
import QtQuick.Layouts
import org.kde.plasma.components as PlasmaComponents
import org.kde.kirigami as Kirigami

Item {
    id:root
    signal runRequested()
    property string status: ""
    property string lastRun: ""
    property string version: ""
    property string color: "LightBlue"
    property ListModel history

    Layout.preferredWidth: Kirigami.Units.gridUnit * 25
    Layout.preferredHeight: Kirigami.Units.gridUnit * 14

    // High level status
    Rectangle {
        x: Kirigami.Units.gridUnit * 1
        y: Kirigami.Units.gridUnit * 1
        width: Kirigami.Units.gridUnit * 2
        height: Kirigami.Units.gridUnit * 2
        radius: Kirigami.Units.gridUnit
        color: root.color
    }
    PlasmaComponents.Label {
        x: Kirigami.Units.gridUnit * 4
        y: Kirigami.Units.gridUnit * 1
        height: Kirigami.Units.gridUnit * 2
        verticalAlignment: Text.AlignVCenter
        font.pointSize: Kirigami.Theme.defaultFont.pointSize * 2
        text: root.status
    }

    // Last run time and observed version
    PlasmaComponents.Label {
        x: Kirigami.Units.gridUnit * 1
        y: Kirigami.Units.gridUnit * 4
        font.italic: true
        opacity: 0.6
        text: "Last Run"
    }
    PlasmaComponents.Label {
        x: Kirigami.Units.gridUnit * 1
        y: Kirigami.Units.gridUnit * 5
        text: root.lastRun
    }

    PlasmaComponents.Label {
        x: Kirigami.Units.gridUnit * 1
        y: Kirigami.Units.gridUnit * 7
        font.italic: true
        text: "Overseer"
        opacity: 0.6
    }
    PlasmaComponents.Label {
        x: Kirigami.Units.gridUnit * 1
        y: Kirigami.Units.gridUnit * 8
        text: "v" + root.version
    }

    // Restart button
    PlasmaComponents.Button {
        anchors {
            left: parent.left
            bottom: parent.bottom
            leftMargin: Kirigami.Units.gridUnit
            bottomMargin: Kirigami.Units.gridUnit
        }
        width: (parent.width * 0.4) - Kirigami.Units.gridUnit * 2
        height: Kirigami.Units.gridUnit * 2
        text: "Run now"
        onClicked: root.runRequested()
    }

    // History
    PlasmaComponents.ScrollView {
        anchors {
            right: parent.right
            bottom: parent.bottom
            top: parent.top
            topMargin: Kirigami.Units.gridUnit * 4
            bottomMargin: Kirigami.Units.gridUnit
        }
        width: parent.width * 0.6

        ListView {
            id: historyView
            focus: true
            boundsBehavior: Flickable.StopAtBounds
            model: root.history
            delegate: Row {
                x: Kirigami.Units.gridUnit * 0.5
                width: historyView.width - x
                PlasmaComponents.Label {
                    height: implicitHeight
                    width: Kirigami.Units.gridUnit * 7
                    horizontalAlignment: Text.AlignLeft
                    text: model.start
                }
                PlasmaComponents.Label {
                    height: implicitHeight
                    width: Kirigami.Units.gridUnit * 4.5
                    horizontalAlignment: Text.AlignRight
                    text: model.duration
                }
                PlasmaComponents.Label {
                    height: implicitHeight
                    width: Kirigami.Units.gridUnit * 1.5
                    horizontalAlignment: Text.AlignRight
                    text: model.exitCode
                }
            }
            section {
                property: "command"
                criteria: ViewSection.FullString
                delegate: PlasmaComponents.Label {
                    required property string section
                    font.italic: true
                    opacity: 0.6
                    width: historyView.width
                    topPadding: 5
                    elide: Text.ElideRight
                    text: section
                }
            }
        }
    }
    
}