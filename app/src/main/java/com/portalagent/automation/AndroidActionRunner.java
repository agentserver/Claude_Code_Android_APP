package com.portalagent.automation;

public interface AndroidActionRunner {

    void runStep(ActionStep step) throws Exception;

    ScreenFingerprint currentFingerprint();

    boolean matches(ScreenFingerprint expected);
}
