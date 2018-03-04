package com.cengenes.bitbucket.repo.cloner.api.service;


import com.cengenes.bitbucket.repo.cloner.api.model.request.CloneRequest;
import com.cengenes.bitbucket.repo.cloner.api.model.response.RepoCloneResponse;
import com.cengenes.bitbucket.repo.cloner.api.model.response.ResponseStatusType;
import org.springframework.stereotype.Service;


import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Optional;


@Service
public class CloneCreateRequestService {


    private Logger log = LogManager.getLogger(this.getClass());


    private final String REST_API_SUFFIX;
    private final String API_PROJECTS;
    private final String API_REPOSITORIES;

    public CloneCreateRequestService() {
        API_PROJECTS = "/projects";
        API_REPOSITORIES = "/repos";
        REST_API_SUFFIX = "/rest/api/1.0";
    }


    public RepoCloneResponse cloneRepos(CloneRequest cloneRequest) {

        final RepoCloneResponse repoCloneResponse = new RepoCloneResponse();

        // Obtain repos
       final Optional<JSONArray> repositories;

        try {
            repositories = obtainRepositories(cloneRequest);

            if(repositories.isPresent()){
            for (int k = 0; k < repositories.get().length(); k++) {
                JSONObject repository = (JSONObject) repositories.get().get(k);
                String repoName = repository.getString("name");
                log.debug("Repository name: {}", repoName);

                log.debug("Repository local directory where clone to {}", cloneRequest.getLocalRepoDirectory());
                final JSONArray cloneURLs = (JSONArray) ((JSONObject) repository.get("links")).get("clone");
                for (int r = 0; r < cloneURLs.length(); r++) {
                    if (((JSONObject) cloneURLs.get(r)).get("name").toString().equals("http")) {
                        log.debug("HTTP repository link for clone found.");
                        cloneRepository(cloneRequest);
                    } else {
                        log.debug(((JSONObject) cloneURLs.get(r)).get("name").toString());
                    }

                }
            }
        }
            repoCloneResponse.setStatus(ResponseStatusType.SUCCESS.getValue());


        } catch (UnirestException e) {
            repoCloneResponse.setStatus(ResponseStatusType.FAILURE.getValue());
            log.error("Error on obtaining Repos {}", e);
        }


        return repoCloneResponse;
    }


    private Optional<JSONArray> obtainRepositories(CloneRequest cloneRequest) throws UnirestException {
        log.info("Obtaining repos from {}", cloneRequest.getProjectKey());

        final String REPO_URL = cloneRequest.getBitbucketServerUrl() + REST_API_SUFFIX + API_PROJECTS
                + "/" + cloneRequest.getProjectKey() + API_REPOSITORIES + "?limit=100";

        return Optional.of(Unirest.get(REPO_URL)
                .basicAuth(cloneRequest.getUserName(), cloneRequest.getPassword())
                .header("accept", "application/json")
                .asJson()
                .getBody()
                .getObject().getJSONArray("values"));

    }

    private void cloneRepository(CloneRequest cloneRequest) {

        log.info("Going to clone repo {},Repository will be stored at {}", cloneRequest.getBitbucketServerUrl(),cloneRequest.getLocalRepoDirectory());

        try {
            Git.cloneRepository()
                    .setURI(cloneRequest.getBitbucketServerUrl())
                    .setDirectory(new File(cloneRequest.getLocalRepoDirectory()))
                    .setCloneAllBranches(true)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(cloneRequest.getUserName(), cloneRequest.getPassword()))
                    .call();
        } catch (GitAPIException e) {
            log.error("Error on cloning repository from {} into local directory {}" ,cloneRequest.getBitbucketServerUrl() ,cloneRequest.getLocalRepoDirectory() + ". Check the path.");
        }
    }
}