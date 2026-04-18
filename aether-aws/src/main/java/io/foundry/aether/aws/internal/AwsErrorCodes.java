/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.internal;

public interface AwsErrorCodes {

    String S3_NO_SUCH_KEY = "aws.s3.NoSuchKey";
    String S3_NO_SUCH_BUCKET = "aws.s3.NoSuchBucket";
    String EC2_INVALID_INSTANCE_ID = "aws.ec2.InvalidInstanceID.NotFound";
    String AUTH_SIGNATURE_MISMATCH = "aws.auth.SignatureDoesNotMatch";
}
