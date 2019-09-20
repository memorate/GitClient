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
import java.util.Collection;
import java.util.Optional;

public class GitClient implements Serializable {

    private static final long serialVersionUID = 6662367073790643915L;

    private static final Logger logger = LoggerFactory.getLogger(GitClient.class);

    private static final String BRANCH_PREFIX = "refs/heads/";
    private static final String TAG_PREFIX = "refs/tags/";
    private static final String HTTP_URL_PREFIX = "http";


    private String url;

    private String originalReference;

    private RepoCredit repoCredit;

    public GitClient(String url, String originalReference, RepoCredit repoCredit) {
        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(originalReference) || ObjectUtils.isEmpty(repoCredit) || StringUtils.isEmpty(repoCredit.getUsername())) {
            logger.error("new GitlabClient() error.Initialize GitlabClient's parameters can not be null.Url = {}, originalReference = {}", url, originalReference);
            throw new GitlabClientException(RetCode.REPO_CREDENTIAL_IS_NULL);
        }
        this.url = url;
        this.originalReference = originalReference;
        this.repoCredit = repoCredit;
    }

    public Optional<String> getCommitId() {
        //http和ssh两种授信方式
        if (url.startsWith(HTTP_URL_PREFIX)) {
            return Optional.ofNullable(getCommitIdFinal(generateCredentialsProvider()));
        } else {
            return Optional.ofNullable(getCommitIdFinal(generateTransportConfigCallback()));
        }
    }

    private <T> String getCommitIdFinal(T gitCredit) {
        try {
            Collection<Ref> refCollection = null;
            if (gitCredit instanceof CredentialsProvider) {
                refCollection = Git.lsRemoteRepository().setCredentialsProvider((CredentialsProvider) gitCredit).setHeads(true).setTags(true).setRemote(url).call();
            } else if (gitCredit instanceof TransportConfigCallback) {
                refCollection = Git.lsRemoteRepository().setTransportConfigCallback((TransportConfigCallback) gitCredit).setHeads(true).setTags(true).setRemote(url).call();
            }
            if (refCollection == null) {
                logger.info("GitlabClient obtained commitId is null. originalReference = {},url = {}username = {}", originalReference, url, repoCredit.getUsername());
                return null;
            }
            String branch = BRANCH_PREFIX + originalReference;
            String tag = TAG_PREFIX + originalReference;
            for (Ref ref : refCollection) {
                //不论用户输入git的分支、标签或者commitId，都转换成commitId
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

    /**
     * 用于生成git远程访问所需要的username-password授信
     */
    private CredentialsProvider generateCredentialsProvider() {
        if (StringUtils.isEmpty(repoCredit.getPassword())) {
            logger.error("generateCredentialsProvider() error.password can not be null.");
            throw new GitlabClientException(RetCode.MISS_ESSENTIAL_PARAM);
        }
        return new UsernamePasswordCredentialsProvider(repoCredit.getUsername(), repoCredit.getPassword());
    }

    /**
     * 用于生成git远程访问所需要的ssh授信
     */
    private TransportConfigCallback generateTransportConfigCallback() {
        if (StringUtils.isEmpty(repoCredit.getSshKey())) {
            logger.error("generateTransportConfigCallback() error.sshKey can not be null.");
            throw new GitlabClientException(RetCode.MISS_ESSENTIAL_PARAM);
        }
        SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                //跳过StrictHostKeyChecking，若不跳过，当~/.ssh/known_hosts中没有要访问的域名时会报错
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
