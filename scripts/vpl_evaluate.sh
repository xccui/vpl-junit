#!/bin/bash
#load common script and check programs

# Enter timeout for unittests here
vpl_junit_timeout=1

# use the latest available version
vpl_junit_version=$(basename  $(ls vpl-junit*) .b64)

. common_script.sh
check_program javac
check_program java
get_source_files java

#compile all .java files

export CLASSPATH=$CLASSPATH:./$vpl_junit_version
javac -J-Xmx16m -Xlint:deprecation *.java

if [ "$?" -ne "0" ] ; then
  echo "Not compiled"
  exit 0
fi

cat common_script.sh > vpl_execution
echo "timeout $vpl_junit_timeout java -jar $vpl_junit_version" >> vpl_execution
chmod +x vpl_execution