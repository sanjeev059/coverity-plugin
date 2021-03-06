/*******************************************************************************
 * Copyright (c) 2018 Synopsys, Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Synopsys, Inc - initial implementation and documentation
 *******************************************************************************/
package jenkins.plugins.coverity.ws;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.xml.datatype.DatatypeConfigurationException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.coverity.ws.v9.CovRemoteServiceException_Exception;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import jenkins.model.Jenkins;
import jenkins.plugins.coverity.CIMInstance;
import jenkins.plugins.coverity.CIMStream;
import jenkins.plugins.coverity.CoverityBuildAction;
import jenkins.plugins.coverity.CoverityPublisher;
import jenkins.plugins.coverity.CoverityPublisher.DescriptorImpl;
import jenkins.plugins.coverity.DefectFilters;
import jenkins.plugins.coverity.Utils.CoverityPublisherBuilder;
import jenkins.plugins.coverity.Utils.TestableConsoleLogger;
import jenkins.plugins.coverity.ws.TestWebServiceFactory.TestDefectService;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Jenkins.class)
public class DefectReaderTest {
    @Mock
    private Jenkins jenkins;

    @Mock
    private AbstractBuild<?, ?> build;

    @Mock
    private BuildListener listener;

    @Mock
    private DescriptorImpl descriptor;

    private TestableConsoleLogger consoleLogger;
    private TestDefectService defectService;
    private static final String cimInstanceName = "cim-instance";

    @Before
    public void setup() throws IOException {
        // setup jenkins
        PowerMockito.mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkins);

        // setup console logger
        consoleLogger = new TestableConsoleLogger();
        when(listener.getLogger()).thenReturn(consoleLogger.getPrintStream());

        // setup global configuration (DescriptorImpl) with TestWebService
        CIMInstance cimInstance = mock(CIMInstance.class);
        defectService = (TestDefectService)new TestWebServiceFactory().getDefectService(cimInstance);
        when(cimInstance.getDefectService()).thenReturn(defectService);
        when(descriptor.getInstance(any(CoverityPublisher.class))).thenReturn(cimInstance);
        when(jenkins.getDescriptorOrDie(CoverityPublisher.class)).thenReturn(descriptor);
    }

    private DefectFilters getMatchingDefectFilters(){
        try {
            DefectFilters defectFilters = new DefectFilters();
            defectFilters.setActions(Arrays.asList("Undecided"));
            defectFilters.setImpacts(Arrays.asList("High", "Medium", "Low"));
            defectFilters.setClassifications(Arrays.asList("Unclassified"));
            defectFilters.setSeverities(Arrays.asList("Unspecified", "Major", "Moderate", "Minor"));
            defectFilters.setComponents(Arrays.asList("Default.Other"));
            defectFilters.setCheckers(Arrays.asList("TEST_CHECKER"));
            defectFilters.setCutOffDate("2017-01-01");

            return defectFilters;
        } catch(Descriptor.FormException e) {
            return null;
        }
    }

    @Test
    public void getLatestDefectsForBuild_withNoDefectFilters_addDefectsToBuildAction() throws ParseException, DatatypeConfigurationException, IOException, CovRemoteServiceException_Exception {

        when(jenkins.getRootUrl()).thenReturn("rootUrl/");
        when(build.getUrl()).thenReturn("buildUrl/");

        CIMStream cimStream  = new CIMStream(cimInstanceName, "test-project", "test-stream");

        CoverityPublisher publisher = new CoverityPublisherBuilder().withCimStream(cimStream).build();

        defectService.setupMergedDefects(10);

        DefectReader reader = new DefectReader(build, listener, publisher);

        reader.getLatestDefectsForBuild();

        // assert build action added to build with expected defect count
        ArgumentCaptor<CoverityBuildAction> buildAction = ArgumentCaptor.forClass(CoverityBuildAction.class);
        verify(build).addAction(buildAction.capture());
        assertEquals(10, buildAction.getValue().getDefects().size());

        // verify all expected log messages were written
        consoleLogger.verifyMessages(
            "[Coverity] Fetching defects for stream \"test-stream\"",
            "[Coverity] Found 10 defects matching all filters",
            "Coverity details: rootUrl/buildUrl/coverity_defects");
    }

    @Test
    public void getLatestDefectsForBuild_withMatchingDefectFilters_addDefectsToBuildAction() throws ParseException, DatatypeConfigurationException, IOException, CovRemoteServiceException_Exception {
        when(jenkins.getRootUrl()).thenReturn("rootUrl/");
        when(build.getUrl()).thenReturn("buildUrl/");

        CIMStream cimStream = new CIMStream(cimInstanceName, "test-project", "test-stream");
        cimStream.setDefectFilters(getMatchingDefectFilters());

        CoverityPublisher publisher = new CoverityPublisherBuilder().withCimStream(cimStream).build();

        defectService.setupMergedDefects(3);

        DefectReader reader = new DefectReader(build, listener, publisher);

        reader.getLatestDefectsForBuild();

        // assert build action added to build with expected defect count
        ArgumentCaptor<CoverityBuildAction> buildAction = ArgumentCaptor.forClass(CoverityBuildAction.class);
        verify(build).addAction(buildAction.capture());
        assertEquals(3, buildAction.getValue().getDefects().size());

        // verify all expected log messages were written
        consoleLogger.verifyMessages(
            "[Coverity] Fetching defects for stream \"test-stream\"",
            "[Coverity] Found 3 defects matching all filters",
            "Coverity details: rootUrl/buildUrl/coverity_defects");
    }

    @Test
    public void getLatestDefectsForBuild_withOverOneThousandMatchingDefects_addDefectsToBuildAction() throws Descriptor.FormException, ParseException, DatatypeConfigurationException, IOException, CovRemoteServiceException_Exception {
        when(jenkins.getRootUrl()).thenReturn("rootUrl/");
        when(build.getUrl()).thenReturn("buildUrl/");

        CIMStream cimStream = new CIMStream(cimInstanceName, "test-project", "test-stream");
        cimStream.setDefectFilters(getMatchingDefectFilters());

        CoverityPublisher publisher = new CoverityPublisherBuilder().withCimStream(cimStream).build();

        defectService.setupMergedDefects(3750);

        DefectReader reader = new DefectReader(build, listener, publisher);

        reader.getLatestDefectsForBuild();

        // assert build action added to build with expected defect count
        ArgumentCaptor<CoverityBuildAction> buildAction = ArgumentCaptor.forClass(CoverityBuildAction.class);
        verify(build).addAction(buildAction.capture());
        assertEquals(3750, buildAction.getValue().getDefects().size());

        // verify all expected log messages were written
        consoleLogger.verifyMessages(
            "[Coverity] Fetching defects for stream \"test-stream\"",
            "[Coverity] Fetching defects for stream \"test-stream\" (fetched 1,000 of 3,750)",
            "[Coverity] Fetching defects for stream \"test-stream\" (fetched 2,000 of 3,750)",
            "[Coverity] Fetching defects for stream \"test-stream\" (fetched 3,000 of 3,750)",
            "[Coverity] Found 3,750 defects matching all filters",
            "Coverity details: rootUrl/buildUrl/coverity_defects");
    }

    @Test
    public void getLatestDefectsForBuild_withMatchingDefectFilters_setsBuildResultAsFailed() throws ParseException, DatatypeConfigurationException, IOException, CovRemoteServiceException_Exception {
        // set initial state as success (result can only get worse)
        when(build.getResult()).thenReturn(Result.SUCCESS);

        CIMStream cimStream = new CIMStream(cimInstanceName, "test-project", "test-stream");
        cimStream.setDefectFilters(getMatchingDefectFilters());

        CoverityPublisher publisher = new CoverityPublisherBuilder().withCimStream(cimStream).withFailBuild(true).build();

        defectService.setupMergedDefects(3);

        DefectReader reader = new DefectReader(build, listener, publisher);

        reader.getLatestDefectsForBuild();

        // assert build action added to build with expected defect count
        ArgumentCaptor<CoverityBuildAction> buildAction = ArgumentCaptor.forClass(CoverityBuildAction.class);
        verify(build).addAction(buildAction.capture());
        assertEquals(3, buildAction.getValue().getDefects().size());

        // verify all expected log messages were written
        consoleLogger.verifyMessages(
            "[Coverity] Fetching defects for stream \"test-stream\"",
            "[Coverity] Found 3 defects matching all filters");
    }

    @Test
    public void getLatestDefectsForBuild_skipsFetchingDefects() throws Descriptor.FormException, ParseException, DatatypeConfigurationException, IOException, CovRemoteServiceException_Exception {
        CoverityPublisher publisher = new CoverityPublisherBuilder().withSkipFetchingDefects(true).build();

        DefectReader reader = new DefectReader(build, listener, publisher);

        reader.getLatestDefectsForBuild();

        // verify no log messages were written
        consoleLogger.verifyMessages();
    }
}
