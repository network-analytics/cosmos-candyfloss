FROM eclipse-temurin:21-jdk

LABEL authors="ahmed.elhassany@swisscom.com"

#Static un-privileged container user
ENV DAISY_USER_GROUP_ID 1000
ENV DAISY_USER_ID 1000
ENV DAISY_GROUP_NAME container-system-group
ENV DAISY_USER_NAME container-system-user
RUN groupadd ${DAISY_GROUP_NAME} --gid ${DAISY_USER_GROUP_ID}
RUN useradd --system --uid ${DAISY_USER_ID} --gid ${DAISY_USER_GROUP_ID} ${DAISY_USER_NAME}

#Base folder within the container
ENV DAISY_FOLDER_BASE /data/daisy
RUN mkdir -p ${DAISY_FOLDER_BASE} && \
    chown -R ${DAISY_USER_ID}:${DAISY_USER_GROUP_ID} ${DAISY_FOLDER_BASE}
WORKDIR ${DAISY_FOLDER_BASE}

ENV DAISY_KS_FOLDER_BASE  ${DAISY_FOLDER_BASE}/kafka-streams
ENV VOLUME_DAISY_KS_STATE ${DAISY_KS_FOLDER_BASE}/state
ENV VOLUME_DAISY_KS_LOGS  ${DAISY_KS_FOLDER_BASE}/logs
ENV VOLUME_DAISY_KS_TMP   /tmp

WORKDIR ${DAISY_KS_FOLDER_BASE}
RUN chown -R ${DAISY_USER_ID}:${DAISY_USER_GROUP_ID} ${DAISY_KS_FOLDER_BASE}


#Explicitly create volume dirs and set their access for the unpriviledged user
RUN mkdir -p ${VOLUME_DAISY_KS_STATE} && \
    chown -R ${DAISY_USER_ID}:${DAISY_USER_GROUP_ID} ${VOLUME_DAISY_KS_STATE}
RUN mkdir -p ${VOLUME_DAISY_KS_TMP} && \
    chown -R ${DAISY_USER_ID}:${DAISY_USER_GROUP_ID} ${VOLUME_DAISY_KS_TMP}
RUN mkdir -p ${VOLUME_DAISY_KS_LOGS} && \
    chown -R ${DAISY_USER_ID}:${DAISY_USER_GROUP_ID} ${VOLUME_DAISY_KS_LOGS}

# Necessary to propagate into the volume data and user access rights already specified within the container:
# - must be put as last instruction to encompass all the previous changes on the target path
# - https://container-solutions.com/understanding-volumes-docker/
VOLUME ${DAISY_KS_FOLDER_BASE}
VOLUME ${VOLUME_DAISY_KS_STATE}
VOLUME ${VOLUME_DAISY_KS_TMP}
VOLUME ${VOLUME_DAISY_KS_LOGS}

USER ${DAISY_USER_ID}