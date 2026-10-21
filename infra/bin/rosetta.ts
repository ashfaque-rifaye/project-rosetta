#!/usr/bin/env node
import { App } from "aws-cdk-lib";
import { RosettaStack } from "../lib/rosetta-stack";

const app = new App();
new RosettaStack(app, "ProjectRosetta", {
  env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION ?? "us-east-1" },
  description: "Project Rosetta: agent crew backend for the Fire TV app (Amazon Build, Ship, Shape hackathon)",
});
