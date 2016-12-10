#====================================================================
# CMake configuration for the Overseer plasmoid.
#
# WARNING: This file needs some seriour work and may not even work.
#          I've been using "plasmapkg2 --install" to install instead.
#
# Copyright Jody Sankey 2016
#
# This software may be modified and distributed under the terms of
# the MIT license. See the LICENSE.md file for details.
#====================================================================

# Set minimum CMake version
cmake_minimum_required(VERSION 2.8.12)

find_package(ECM REQUIRED NO_MODULE)
# Needed by find_package(KF5Plasma) below.
set(CMAKE_MODULE_PATH ${ECM_MODULE_PATH} ${ECM_KDE_MODULE_DIR} ${CMAKE_MODULE_PATH})

# Locate plasma_install_package macro.
find_package(KF5Plasma REQUIRED)

# Add installatation target ("make install").
plasma_install_package(plasmoid com.jsankey.overseer.monitor)