package iurii.bulanov.benchmark.evaluation.reporting

import iurii.bulanov.benchmark.evaluation.CheckoutEvaluation
import iurii.bulanov.benchmark.evaluation.ContentMetrics
import iurii.bulanov.benchmark.evaluation.ConversionEvaluation
import iurii.bulanov.benchmark.evaluation.EvaluationResult
import iurii.bulanov.benchmark.evaluation.EvaluationWarning
import iurii.bulanov.benchmark.evaluation.FileCoverageMetrics
import iurii.bulanov.benchmark.evaluation.NullabilityMetrics
import iurii.bulanov.benchmark.evaluation.QualityMetrics
import iurii.bulanov.benchmark.evaluation.StructuralMetrics
import iurii.bulanov.benchmark.evaluation.StructuralNameDiff
import iurii.bulanov.benchmark.evaluation.StructuralNameDiffs
import iurii.bulanov.logging.JsonEncoder

/**
 * Renders the machine-readable evaluator report body.
 */
internal class EvaluationReportJsonRenderer : EvaluationReportRenderer {
    /**
     * Renders [result] as deterministic JSON with a trailing newline.
     */
    override fun render(result: EvaluationResult): String =
        JsonEncoder.encode(
            linkedMapOf(
                "benchmark" to benchmarkJson(result),
                "paths" to pathsJson(result),
                "checkout" to checkoutJson(result.checkout),
                "conversion" to conversionJson(result.conversion),
                "file_coverage" to fileCoverageJson(result.fileCoverage),
                "structure" to structureJson(result.structure),
                "content" to contentJson(result.content),
                "nullability" to nullabilityJson(result.nullability),
                "quality" to qualityJson(result.quality),
                "analysis" to assignmentAnalysisJson(result),
                "counts" to countsJson(result),
                "status" to result.status.name.lowercase(),
                "warnings" to warningsJson(result.warnings),
            ),
        ) + "\n"

    private fun benchmarkJson(result: EvaluationResult): Map<String, Any?> =
        linkedMapOf(
            "id" to result.config.id,
            "name" to result.config.name,
            "role" to result.config.role,
            "kind" to result.kind.id,
            "repository_source" to result.config.repository.source,
            "repository_upstream" to result.config.repository.upstream,
            "repository_ref" to result.config.repository.ref,
        )

    private fun pathsJson(result: EvaluationResult): Map<String, Any?> =
        linkedMapOf(
            "checkout_directory" to result.checkoutDirectory.toString(),
            "generated_kotlin_directory" to result.generatedKotlinDirectory.toString(),
            "conversion_report" to result.conversionReportPath.toString(),
            "checkout_report" to result.checkoutReportPath.toString(),
            "report_directory" to result.reportDirectory.toString(),
        )

    private fun checkoutJson(checkout: CheckoutEvaluation): Map<String, Any?> =
        linkedMapOf(
            "available" to checkout.available,
            "benchmark_id" to checkout.benchmarkId,
            "build_status" to checkout.buildStatus,
            "java_file_count" to checkout.javaFileCount,
            "run_build" to checkout.runBuild,
        )

    private fun conversionJson(conversion: ConversionEvaluation): Map<String, Any?> =
        linkedMapOf(
            "available" to conversion.available,
            "benchmark_id" to conversion.benchmarkId,
            "status" to conversion.status,
            "source_java_file_count" to conversion.sourceJavaFileCount,
            "generated_kotlin_file_count" to conversion.generatedKotlinFileCount,
            "warning_count" to conversion.warningCount,
            "error_count" to conversion.errorCount,
            "warnings" to conversion.warnings,
            "errors" to conversion.errors,
        )

    private fun assignmentAnalysisJson(result: EvaluationResult): Map<String, Any?> =
        linkedMapOf(
            "benchmark_role" to result.config.role,
            "analysis_method" to "structural_heuristics",
            "conversion_status" to result.conversion.status,
            "evaluation_status" to result.status.name.lowercase(),
            "file_coverage_percent" to result.fileCoverage.coveragePercent,
            "missing_output_count" to result.fileCoverage.missingKotlinFiles.size,
            "content_warning_count" to result.content.findings.size,
            "nullability_warning_count" to result.nullability.findings.size,
            "quality_warning_count" to result.quality.findings.size,
        )

    private fun countsJson(result: EvaluationResult): Map<String, Any?> =
        linkedMapOf(
            "java_file_count" to result.fileCoverage.javaFileCount,
            "kotlin_file_count" to result.fileCoverage.kotlinFileCount,
            "warning_count" to result.warnings.size,
        )

    private fun warningsJson(warnings: List<EvaluationWarning>): List<Map<String, Any?>> =
        warnings.map { warning ->
            linkedMapOf(
                "code" to warning.code,
                "message" to warning.message,
                "path" to warning.path,
                "count" to warning.count,
            )
        }

    private fun fileCoverageJson(metrics: FileCoverageMetrics): Map<String, Any?> =
        linkedMapOf(
            "java_file_count" to metrics.javaFileCount,
            "kotlin_file_count" to metrics.kotlinFileCount,
            "matched_kotlin_file_count" to metrics.matchedKotlinFileCount,
            "coverage_percent" to metrics.coveragePercent,
            "missing_kotlin_files" to metrics.missingKotlinFiles,
            "unexpected_kotlin_files" to metrics.unexpectedKotlinFiles,
            "empty_generated_files" to metrics.emptyGeneratedFiles,
            "package_preserved_count" to metrics.packagePreservedCount,
            "package_preservation_percent" to metrics.packagePreservationPercent,
            "package_mismatch_files" to metrics.packageMismatchFiles,
        )

    private fun structureJson(metrics: StructuralMetrics): Map<String, Any?> =
        linkedMapOf(
            "java_top_level_declaration_count" to metrics.javaTopLevelDeclarationCount,
            "kotlin_top_level_declaration_count" to metrics.kotlinTopLevelDeclarationCount,
            "java_class_like_count" to metrics.javaClassLikeCount,
            "kotlin_class_like_count" to metrics.kotlinClassLikeCount,
            "java_interface_count" to metrics.javaInterfaceCount,
            "kotlin_interface_count" to metrics.kotlinInterfaceCount,
            "java_enum_count" to metrics.javaEnumCount,
            "kotlin_enum_count" to metrics.kotlinEnumCount,
            "java_method_count" to metrics.javaMethodCount,
            "kotlin_function_count" to metrics.kotlinFunctionCount,
            "public_api_name_overlap_count" to metrics.publicApiNameOverlapCount,
            "missing_public_api_names" to metrics.missingPublicApiNames,
            "kotlin_only_public_api_names" to metrics.kotlinOnlyPublicApiNames,
            "name_diffs" to nameDiffsJson(metrics.nameDiffs),
        )

    private fun contentJson(metrics: ContentMetrics): Map<String, Any?> =
        linkedMapOf(
            "matched_file_count" to metrics.matchedFileCount,
            "java_non_empty_method_count" to metrics.javaNonEmptyMethodCount,
            "kotlin_non_empty_function_count" to metrics.kotlinNonEmptyFunctionCount,
            "java_empty_method_count" to metrics.javaEmptyMethodCount,
            "kotlin_empty_function_count" to metrics.kotlinEmptyFunctionCount,
            "missing_kotlin_bodies" to metrics.missingKotlinBodies,
            "content_shape_mismatch_files" to metrics.contentShapeMismatchFiles,
            "java_return_count" to metrics.javaReturnCount,
            "kotlin_return_count" to metrics.kotlinReturnCount,
            "java_branch_count" to metrics.javaBranchCount,
            "kotlin_branch_count" to metrics.kotlinBranchCount,
            "java_loop_count" to metrics.javaLoopCount,
            "kotlin_loop_count" to metrics.kotlinLoopCount,
            "java_throw_count" to metrics.javaThrowCount,
            "kotlin_throw_count" to metrics.kotlinThrowCount,
            "java_try_count" to metrics.javaTryCount,
            "kotlin_try_count" to metrics.kotlinTryCount,
            "java_function_declaration_count" to metrics.javaFunctionDeclarationCount,
            "kotlin_function_declaration_count" to metrics.kotlinFunctionDeclarationCount,
            "content_shape_preserved_file_count" to metrics.contentShapePreservedFileCount,
            "content_shape_mismatch_file_count" to metrics.contentShapeMismatchFileCount,
            "return_preservation_ratio" to metrics.returnPreservationRatio,
            "branch_preservation_ratio" to metrics.branchPreservationRatio,
            "throw_preservation_ratio" to metrics.throwPreservationRatio,
            "try_preservation_ratio" to metrics.tryPreservationRatio,
            "control_flow_fidelity_score" to metrics.controlFlowFidelityScore,
            "content_shape_preservation_rate" to metrics.contentShapePreservationRate,
            "java_return_density" to metrics.javaReturnDensity,
            "kotlin_return_density" to metrics.kotlinReturnDensity,
            "return_statement_density_preservation" to metrics.returnStatementDensityPreservation,
            "java_branch_complexity_index" to metrics.javaBranchComplexityIndex,
            "kotlin_branch_complexity_index" to metrics.kotlinBranchComplexityIndex,
            "branch_complexity_index_preservation" to metrics.branchComplexityIndexPreservation,
            "findings" to findingsJson(metrics.findings),
        )

    private fun nullabilityJson(metrics: NullabilityMetrics): Map<String, Any?> =
        linkedMapOf(
            "java_nullable_annotation_count" to metrics.javaNullableAnnotationCount,
            "java_not_null_annotation_count" to metrics.javaNotNullAnnotationCount,
            "kotlin_nullable_type_count" to metrics.kotlinNullableTypeCount,
            "contradictory_nullability_patterns" to metrics.contradictoryNullabilityPatterns,
            "null_comparison_count" to metrics.nullComparisonCount,
            "nullability_cast_count" to metrics.nullabilityCastCount,
            "safe_call_count" to metrics.safeCallCount,
            "total_nullability_operation_count" to metrics.totalNullabilityOperationCount,
            "nullability_inference_accuracy" to metrics.nullabilityInferenceAccuracy,
            "nullable_annotations_not_preserved" to metrics.nullableAnnotationsNotPreserved,
            "not_null_annotations_became_nullable" to metrics.notNullAnnotationsBecameNullable,
            "findings" to findingsJson(metrics.findings),
        )

    private fun nameDiffsJson(nameDiffs: StructuralNameDiffs): Map<String, Any?> =
        linkedMapOf(
            "class_like" to nameDiffJson(nameDiffs.classLike),
            "interfaces" to nameDiffJson(nameDiffs.interfaces),
            "enums" to nameDiffJson(nameDiffs.enums),
            "objects" to nameDiffJson(nameDiffs.objects),
            "class_like_to_object_names" to nameDiffs.classLikeToObjectNames,
            "java_bean_accessors_missing_as_functions" to nameDiffs.javaBeanAccessorNames,
            "java_bean_accessors_backed_by_kotlin_properties" to nameDiffs.javaBeanAccessorNames,
            "functions" to nameDiffJson(nameDiffs.functions),
        )

    private fun nameDiffJson(diff: StructuralNameDiff): Map<String, Any?> =
        linkedMapOf(
            "missing_in_kotlin" to diff.missingInKotlin,
            "kotlin_only" to diff.kotlinOnly,
        )

    private fun qualityJson(metrics: QualityMetrics): Map<String, Any?> =
        linkedMapOf(
            "todo_count" to metrics.todoCount,
            "not_null_assertion_count" to metrics.notNullAssertionCount,
            "not_null_assertion_in_call_count" to metrics.notNullAssertionInCallCount,
            "any_nullable_count" to metrics.anyNullableCount,
            "unresolved_import_count" to metrics.unresolvedImportCount,
            "java_interop_reference_count" to metrics.javaInteropReferenceCount,
            "getter_setter_call_count" to metrics.getterSetterCallCount,
            "nullable_boolean_comparison_count" to metrics.nullableBooleanComparisonCount,
            "eager_property_initialization_count" to metrics.eagerPropertyInitializationCount,
            "findings" to findingsJson(metrics.findings),
        )

    private fun findingsJson(findings: List<EvaluationWarning>): List<Map<String, Any?>> =
        findings.map { finding ->
            linkedMapOf(
                "code" to finding.code,
                "message" to finding.message,
                "path" to finding.path,
                "count" to finding.count,
            )
        }
}
