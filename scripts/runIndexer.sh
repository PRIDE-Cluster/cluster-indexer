#!/bin/bash

##### OPTIONS
# either db or ws
DATA_SOURCE=$1
# either inc or all
INDEX_METHOD=$2
# Number of peaks for spectra search
SPECTRA_SEARCH=5

##### VARIABLES
# the name to give to the LSF job (to be extended with additional info)
JOB_NAME="PRIDE-CLUSTER-INDEXER"
# memory limit in MGb
MEMORY_LIMIT=10000
# log file name
LOG_FILE_NAME="${JOB_NAME}"

bsub -M ${MEMORY_LIMIT} -R "rusage[mem=${MEMORY_LIMIT}]" -q production-rh6 -g /pride_cluster_loader -o /dev/null -J CLUSTER_INDEXER java -Xmx${MEMORY_LIMIT}m -jar ${project.build.finalName}.jar ${DATA_SOURCE} ${INDEX_METHOD} ${SPECTRA_SEARCH}