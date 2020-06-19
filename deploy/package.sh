#!/bin/bash
PROJECT_DIR=/home/jody/files/code/apps/overseer
ARCHIVE=$PROJECT_DIR/deploy/overseer_deployment.tar
 
pushd $PROJECT_DIR
gradle shadowJar
popd

if [ -f $ARCHIVE ] ; then
   echo "Removing existing archive"
   rm $ARCHIVE
fi
 
tar -cvf $ARCHIVE -C $PROJECT_DIR/build/libs OverseerBundle.jar \
   && tar -rvf $ARCHIVE -C $PROJECT_DIR overseer_check.html overseer_check.py plasmoid \
   && tar -rvf $ARCHIVE -C $PROJECT_DIR/deploy INSTALL.txt \
   && echo "Created archive at $ARCHIVE"