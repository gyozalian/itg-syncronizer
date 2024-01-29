package com.itg.sftp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SFTPConfig {

    private String host;
    private final int port = 22;
    private String userName;
    private String password;

}
