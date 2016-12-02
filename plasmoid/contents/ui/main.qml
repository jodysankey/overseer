 /**
  * KDE Plasma script to monitor Overseer execution status
  *
  * Copyright Jody Sankey 2016
  */

import QtQml 2.2
import QtQuick 2.0
import QtWebSockets 1.0
import QtQuick.Controls 1.4
import org.kde.plasma.plasmoid 2.0

Item {
    // Don't ever want an icon, so crudely force very small switch to compactRepresentation
    Plasmoid.switchWidth: 200
    Plasmoid.switchHeight: 200
    Plasmoid.compactRepresentation: CompactRepresentation {}
    Plasmoid.fullRepresentation: CompactRepresentation {}

    // TODO(jody): Consider a true FullRepresentation and moving the models out of
    //             CompactRepresentation and into here.
}
