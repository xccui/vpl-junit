#!/bin/bash

# Enter timeout for unittests here
vpl_junit_timeout=5

#load common script and check programs
. common_script.sh
check_program javac
check_program java
get_source_files java

#compile all .java files

export CLASSPATH=$CLASSPATH:/usr/share/java/junit4.jar
javac -J-Xmx16m -Xlint:deprecation *.java

if [ "$?" -ne "0" ] ; then
  echo "Not compiled"
  exit 0
fi

cat common_script.sh > vpl_execution
echo "timeout $vpl_junit_timeout java -enableassertions -cp $CLASSPATH:/usr/share/java/junit4.jar Solution" >> vpl_execution
chmod +x vpl_execution