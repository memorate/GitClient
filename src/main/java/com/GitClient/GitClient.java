package com.GitClient;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class GitClient implements Serializable {

    private static final long serialVersionUID = 6662367073790643915L;

    private static final Logger logger = LoggerFactory.getLogger(GitClient.class);

    private static final String BRANCH_PREFIX = "refs/heads/";
    private static final String TAG_PREFIX = "refs/tags/";
    private static final String HTTP_URL_PREFIX = "http";


    private String url;

    private String originalReference;

    private RepoCredit repoCredit;

    /**
     * if you want to get commitId, use this constructor to initialize gitClient
     *
     * @param url               is repository's http or ssh url
     * @param originalReference is repository's branchName、tagName or commitId
     * @param repoCredit        is repository's credential,two ways like username-password or ssh private key
     */
    public GitClient(String url, String originalReference, RepoCredit repoCredit) {
        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(originalReference) || ObjectUtils.isEmpty(repoCredit) || StringUtils.isEmpty(repoCredit.getUsername())) {
            logger.error("new GitlabClient() error.Initialize GitlabClient's parameters can not be null.Url = {}, originalReference = {}", url, originalReference);
            throw new GitlabClientException(RetCode.REPO_CREDENTIAL_IS_NULL);
        }
        this.url = url;
        this.originalReference = originalReference;
        this.repoCredit = repoCredit;
    }

    /**
     * if you want to get  repository's branchNames and tagNames,use this constructor to initialize gitClient
     *
     * @param url        is repository's http or ssh url
     * @param repoCredit is repository's credential,two ways like username-password or ssh private key
     */
    public GitClient(String url, RepoCredit repoCredit) {
        if (StringUtils.isEmpty(url) || ObjectUtils.isEmpty(repoCredit) || StringUtils.isEmpty(repoCredit.getUsername())) {
            logger.error("new GitlabClient() error.Initialize GitlabClient's url and repoCredit can not be null.Url = {},", url);
            throw new GitlabClientException(RetCode.REPO_CREDENTIAL_IS_NULL);
        }
        this.url = url;
        this.repoCredit = repoCredit;
    }

    /**
     * get a commitId convert by branchName、tagName or commitId which user input
     *
     * @return a commitId
     */
    public Optional<String> getCommitId() {
        //http or ssh credential
        if (url.startsWith(HTTP_URL_PREFIX)) {
            return Optional.ofNullable(getCommitIdFinal(generateCredentialsProvider()));
        } else {
            return Optional.ofNullable(getCommitIdFinal(generateTransportConfigCallback()));
        }
    }

    /**
     * get all the repository's BranchNames and TagNames
     *
     * @return a Map<String, List<String>>,include two keys are "branches" and "tags",you can get data through these keys
     */
    public Map<String, List<String>> getBranchesAndTags() {
        if (url.startsWith(HTTP_URL_PREFIX)) {
            return getBranchesAndTagsFinal(generateCredentialsProvider());
        } else {
            return getBranchesAndTagsFinal(generateTransportConfigCallback());
        }
    }

    private <T> String getCommitIdFinal(T gitCredit) {
        try {
            Collection<Ref> refCollection = getRefCollection(gitCredit);
            String branch = BRANCH_PREFIX + originalReference;
            String tag = TAG_PREFIX + originalReference;
            for (Ref ref : refCollection) {
                //no matter user input git's branchName、tagName or commitId, always transfer to commitId and output it
                if (branch.equals(ref.getName()) || tag.equals(ref.getName()) || originalReference.equals(ref.getObjectId().getName())) {
                    return ref.getObjectId().getName();
                }
            }
        } catch (Exception e) {
            logger.error("getCommitIdFinal() error. originalReference = {},url = {}username = {}", originalReference, url, repoCredit.getUsername(), e);
            throw new GitlabClientException(RetCode.GIT_CLIENT_ERROR);
        }
        return null;
    }

    private <T> Map<String, List<String>> getBranchesAndTagsFinal(T gitCredit) {
        try {
            Collection<Ref> refCollection = getRefCollection(gitCredit);
            Map<String, List<String>> result = new HashMap<>();
            //split all the branchNames and tagNames
            List<String> branches = refCollection.stream().map(t -> t.getName().split(BRANCH_PREFIX)[1]).sorted().collect(Collectors.toList());
            List<String> tags = refCollection.stream().map(t -> t.getName().split(TAG_PREFIX)[1]).sorted().collect(Collectors.toList());
            result.put("branches", branches);
            result.put("tags", tags);
            return result;
        } catch (Exception e) {
            logger.error("getCommitIdFinal() error. originalReference = {},url = {}username = {}", originalReference, url, repoCredit.getUsername(), e);
            throw new GitlabClientException(RetCode.GIT_CLIENT_ERROR);
        }
    }

    private <T> Collection<Ref> getRefCollection(T gitCredit) {
        try {
            Collection<Ref> refCollection = null;
            if (gitCredit instanceof CredentialsProvider) {
                refCollection = Git.lsRemoteRepository().setCredentialsProvider((CredentialsProvider) gitCredit).setHeads(true).setTags(true).setRemote(url).call();
            } else if (gitCredit instanceof TransportConfigCallback) {
                refCollection = Git.lsRemoteRepository().setTransportConfigCallback((TransportConfigCallback) gitCredit).setHeads(true).setTags(true).setRemote(url).call();
            }
            if (refCollection == null) {
                logger.info("GitlabClient obtained commitId is null. originalReference = {},url = {}username = {}", originalReference, url, repoCredit.getUsername());
                throw new GitlabClientException(RetCode.GIT_GET_REF_NULL);
            }
            return refCollection;
        } catch (Exception e) {
            logger.error("getRefCollection() error. originalReference = {},url = {}username = {}", originalReference, url, repoCredit.getUsername(), e);
            throw new GitlabClientException(RetCode.GIT_GET_REF_ERROR);
        }
    }

    /**
     * generate UsernamePasswordCredentialsProvider for access git repository remotely by username-password
     */
    private CredentialsProvider generateCredentialsProvider() {
        if (StringUtils.isEmpty(repoCredit.getPassword())) {
            logger.error("generateCredentialsProvider() error.password can not be null.");
            throw new GitlabClientException(RetCode.MISS_ESSENTIAL_PARAM);
        }
        return new UsernamePasswordCredentialsProvider(repoCredit.getUsername(), repoCredit.getPassword());
    }

    /**
     * generate TransportConfig for access git repository remotely by ssh private key
     */
    private TransportConfigCallback generateTransportConfigCallback() {
        if (StringUtils.isEmpty(repoCredit.getSshKey())) {
            logger.error("generateTransportConfigCallback() error.sshKey can not be null.");
            throw new GitlabClientException(RetCode.MISS_ESSENTIAL_PARAM);
        }
        SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                //skip StrictHostKeyChecking，if not，it will report error when known_hosts(~/.ssh/known_hosts) file didn't have the domain name which we are about to access
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch(fs);
                String privateKey = repoCredit.getSshKey();
                if (StringUtils.isEmpty(repoCredit.getSshPassphrase())) {
                    defaultJSch.addIdentity("devcloud", privateKey.getBytes(), null, null);
                } else {
                    defaultJSch.addIdentity("devcloud", privateKey.getBytes(), null, repoCredit.getSshPassphrase().getBytes());
                }
                JSch.setConfig("StrictHostKeyChecking", "no");
                return defaultJSch;
            }
        };
        return transport -> {
            SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(sshSessionFactory);
        };
    }

    public static class GitlabClientException extends RuntimeException {
        private RetCode retCode;

        GitlabClientException(RetCode code) {
            this.retCode = code;
        }

        public RetCode getRetCode() {
            return retCode;
        }
    }

}
