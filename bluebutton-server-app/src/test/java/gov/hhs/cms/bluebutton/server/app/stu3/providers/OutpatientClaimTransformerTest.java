package gov.hhs.cms.bluebutton.server.app.stu3.providers;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.hl7.fhir.dstu3.model.ExplanationOfBenefit;
import org.hl7.fhir.dstu3.model.ExplanationOfBenefit.ItemComponent;
import org.hl7.fhir.exceptions.FHIRException;
import org.junit.Assert;
import org.junit.Test;

import gov.hhs.cms.bluebutton.data.model.rif.OutpatientClaim;
import gov.hhs.cms.bluebutton.data.model.rif.OutpatientClaimLine;
import gov.hhs.cms.bluebutton.data.model.rif.samples.StaticRifResource;
import gov.hhs.cms.bluebutton.data.model.rif.samples.StaticRifResourceGroup;
import gov.hhs.cms.bluebutton.server.app.ServerTestUtils;

/**
 * Unit tests for {@link OutpatientClaimTransformer}.
 */
public final class OutpatientClaimTransformerTest {
	/**
	 * Verifies that {@link OutpatientClaimTransformer#transform(Object)} works
	 * as expected when run against the
	 * {@link StaticRifResource#SAMPLE_A_OUTPATIENT} {@link OutpatientClaim}.
	 * 
	 * @throws FHIRException
	 *             (indicates test failure)
	 */
	@Test
	public void transformSampleARecord() throws FHIRException {
		List<Object> parsedRecords = ServerTestUtils
				.parseData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
		OutpatientClaim claim = parsedRecords.stream().filter(r -> r instanceof OutpatientClaim)
				.map(r -> (OutpatientClaim) r).findFirst().get();

		ExplanationOfBenefit eob = OutpatientClaimTransformer.transform(claim);
		assertMatches(claim, eob);
	}

	/**
	 * Verifies that the {@link ExplanationOfBenefit} "looks like" it should, if
	 * it were produced from the specified {@link OutpatientClaim}.
	 * 
	 * @param claim
	 *            the {@link OutpatientClaim} that the
	 *            {@link ExplanationOfBenefit} was generated from
	 * @param eob
	 *            the {@link ExplanationOfBenefit} that was generated from the
	 *            specified {@link OutpatientClaim}
	 * @throws FHIRException
	 *             (indicates test failure)
	 */
	static void assertMatches(OutpatientClaim claim, ExplanationOfBenefit eob) throws FHIRException {
		TransformerTestUtils.assertNoEncodedOptionals(eob);

		Assert.assertEquals(TransformerUtils.buildEobId(ClaimType.OUTPATIENT, claim.getClaimId()),
				eob.getIdElement().getIdPart());

		TransformerTestUtils.assertIdentifierExists(TransformerConstants.CODING_CCW_CLAIM_ID, claim.getClaimId(),
				eob.getIdentifier());
		TransformerTestUtils.assertIdentifierExists(TransformerConstants.CODING_CCW_CLAIM_GROUP_ID,
				claim.getClaimGroupId().toPlainString(), eob.getIdentifier());
		Assert.assertEquals(TransformerUtils.referencePatient(claim.getBeneficiaryId()).getReference(),
				eob.getPatient().getReference());
		Assert.assertEquals(
				TransformerUtils.referenceCoverage(claim.getBeneficiaryId(), MedicareSegment.PART_B).getReference(),
				eob.getInsurance().getCoverage().getReference());
		Assert.assertEquals("active", eob.getStatus().toCode());

		TransformerTestUtils.assertDateEquals(claim.getDateFrom(),
				eob.getBillablePeriod().getStartElement());
		TransformerTestUtils.assertDateEquals(claim.getDateThrough(),
				eob.getBillablePeriod().getEndElement());

		// test the common field provider number is set as expected in the EOB
		TransformerTestUtils.assertProviderNumber(eob, claim.getProviderNumber());
		
		Assert.assertEquals(claim.getPaymentAmount(), eob.getPayment().getAmount().getValue());

		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.CODING_BBAPI_BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODED_BENEFIT_BALANCE_TYPE_PARTB_DEDUCTIBLE, claim.getDeductibleAmount(),
				eob.getBenefitBalanceFirstRep().getFinancial());
		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.CODING_BBAPI_BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODED_BENEFIT_BALANCE_TYPE_PROFFESIONAL_COMPONENT_CHARGE, claim.getProfessionalComponentCharge(),
				eob.getBenefitBalanceFirstRep().getFinancial());
		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.CODING_BBAPI_BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODED_BENEFIT_BALANCE_TYPE_PARTB_COINSURANCE_AMOUNT, claim.getCoinsuranceAmount(),
				eob.getBenefitBalanceFirstRep().getFinancial());
		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.CODING_BBAPI_BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODED_ADJUDICATION_PROVIDER_PAYMENT_AMOUNT, claim.getProviderPaymentAmount(),
				eob.getBenefitBalanceFirstRep().getFinancial());
		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.CODING_BBAPI_BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODED_BENEFIT_BALANCE_TYPE_BENE_PAYMENT, claim.getBeneficiaryPaymentAmount(),
				eob.getBenefitBalanceFirstRep().getFinancial());

		// Test to ensure common group fields between Inpatient, Outpatient and SNF
		TransformerTestUtils.assertEobCommonGroupInpOutSNFEquals(eob, claim.getBloodDeductibleLiabilityAmount(),
				claim.getOperatingPhysicianNpi(), claim.getOtherPhysicianNpi(), claim.getClaimQueryCode(),
				claim.getMcoPaidSw());

		// Test to ensure common group fields between Inpatient, Outpatient HHA, Hospice
		// and SNF match
		TransformerTestUtils.assertEobCommonGroupInpOutHHAHospiceSNFEquals(eob, claim.getOrganizationNpi(),
				claim.getClaimFacilityTypeCode(), claim.getClaimFrequencyCode(), claim.getClaimNonPaymentReasonCode(),
				claim.getPatientDischargeStatusCode().get(), claim.getClaimServiceClassificationTypeCode(),
				claim.getClaimPrimaryPayerCode(), claim.getAttendingPhysicianNpi(), claim.getTotalChargeAmount(),
				claim.getPrimaryPayerPaidAmount());

		Assert.assertEquals(6, eob.getDiagnosis().size());

		Assert.assertEquals(1, eob.getProcedure().size());
		CCWProcedure ccwProcedure = new CCWProcedure(claim.getProcedure1Code(), claim.getProcedure1CodeVersion(),
				claim.getProcedure1Date());
		TransformerTestUtils.assertHasCoding(ccwProcedure.getFhirSystem().toString(), claim.getProcedure1Code().get(),
				eob.getProcedure().get(0).getProcedureCodeableConcept());
		Assert.assertEquals(Date.from(claim.getProcedure1Date().get().atStartOfDay(ZoneId.systemDefault()).toInstant()),
				eob.getProcedure().get(0).getDate());

		Assert.assertEquals(1, eob.getItem().size());
		ItemComponent eobItem0 = eob.getItem().get(0);
		OutpatientClaimLine claimLine1 = claim.getLines().get(0);
		Assert.assertEquals(new Integer(claimLine1.getLineNumber().intValue()),
				new Integer(eobItem0.getSequence()));

		TransformerTestUtils.assertExtensionCodingEquals(eobItem0,
				TransformerConstants.CODING_FHIR_ACT_INVOICE_GROUP,
				TransformerConstants.CODING_FHIR_ACT_INVOICE_GROUP,
				(TransformerConstants.CODED_ACT_INVOICE_GROUP_CLINICAL_SERVICES_AND_PRODUCTS));

		Assert.assertEquals(claim.getProviderStateCode(), eobItem0.getLocationAddress().getState());

		TransformerTestUtils.assertHasCoding(TransformerConstants.CODING_NDC,
				claimLine1.getNationalDrugCode().get(),
				eobItem0.getService());

		TransformerTestUtils.assertAdjudicationReasonEquals(TransformerConstants.CODED_ADJUDICATION_1ST_ANSI_CD,
				TransformerConstants.CODING_CCW_ADJUDICATION_CATEGORY, claimLine1.getRevCntr1stAnsiCd().get(),
				eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationReasonEquals(TransformerConstants.CODED_ADJUDICATION_2ND_ANSI_CD,
				TransformerConstants.CODING_CCW_ADJUDICATION_CATEGORY, claimLine1.getRevCntr2ndAnsiCd().get(),
				eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationNotPresent(TransformerConstants.CODED_ADJUDICATION_3RD_ANSI_CD,
				eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationNotPresent(TransformerConstants.CODED_ADJUDICATION_4TH_ANSI_CD,
				eobItem0.getAdjudication());

		TransformerTestUtils.assertHasCoding(TransformerConstants.CODING_HCPCS, claimLine1.getHcpcsCode().get(),
				eobItem0.getModifier().get(0));
		TransformerTestUtils.assertHasCoding(TransformerConstants.CODING_HCPCS,
				claimLine1.getHcpcsInitialModifierCode().get(), eobItem0.getModifier().get(1));
		Assert.assertFalse(claimLine1.getHcpcsSecondModifierCode().isPresent());

		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_BLOOD_DEDUCTIBLE,
				claimLine1.getBloodDeductibleAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_CASH_DEDUCTIBLE,
				claimLine1.getCashDeductibleAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(
				TransformerConstants.CODED_ADJUDICATION_WAGE_ADJ_COINSURANCE_AMOUNT,
				claimLine1.getWageAdjustedCoinsuranceAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(
				TransformerConstants.CODED_ADJUDICATION_REDUCED_COINSURANCE_AMOUNT,
				claimLine1.getReducedCoinsuranceAmount(),
				eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_1ST_MSP_AMOUNT,
				claimLine1.getFirstMspPaidAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_2ND_MSP_AMOUNT,
				claimLine1.getSecondMspPaidAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_PROVIDER_PAYMENT_AMOUNT,
				claimLine1.getProviderPaymentAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(
				TransformerConstants.CODED_ADJUDICATION_BENEFICIARY_PAYMENT_AMOUNT,
				claimLine1.getBenficiaryPaymentAmount(),
				eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(
				TransformerConstants.CODED_ADJUDICATION_PATIENT_RESPONSIBILITY_AMOUNT,
				claimLine1.getPatientResponsibilityAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_PAYMENT,
				claimLine1.getPaymentAmount(), eobItem0.getAdjudication());

		// Test to ensure item level fields between Inpatient, Outpatient, HHA, Hopsice
		// and SNF match
		TransformerTestUtils.assertEobCommonItemRevenueEquals(eobItem0, eob, claimLine1.getRevenueCenterCode(),
				claimLine1.getRateAmount(), claimLine1.getTotalChargeAmount(), claimLine1.getNonCoveredChargeAmount(),
				claimLine1.getUnitCount(), claimLine1.getNationalDrugCodeQuantity(),
				claimLine1.getNationalDrugCodeQualifierCode(), claimLine1.getRevenueCenterRenderingPhysicianNPI());

		// verify {@link
		// TransformerUtils#mapEobType(CodeableConcept,ClaimType,Optional,Optional)}
		// method worked as expected for this claim type
		TransformerTestUtils.assertMapEobType(eob.getType(), ClaimType.OUTPATIENT,
				Optional.of(org.hl7.fhir.dstu3.model.codesystems.ClaimType.PROFESSIONAL),
				Optional.of(claim.getNearLineRecordIdCode()), Optional.of(claim.getClaimTypeCode()));
	}

}