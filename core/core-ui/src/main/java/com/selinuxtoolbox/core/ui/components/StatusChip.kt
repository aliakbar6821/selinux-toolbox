package com.selinuxtoolbox.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selinuxtoolbox.core.model.ConflictSeverity
import com.selinuxtoolbox.core.model.RuleClassification
import com.selinuxtoolbox.core.model.SeclabelStatus
import com.selinuxtoolbox.core.ui.theme.CriticalRed
import com.selinuxtoolbox.core.ui.theme.EnforcingGreen
import com.selinuxtoolbox.core.ui.theme.OrphanedGrey
import com.selinuxtoolbox.core.ui.theme.PermissiveAmber
import com.selinuxtoolbox.core.ui.theme.ReviewBlue
import com.selinuxtoolbox.core.ui.theme.SafeGreen
import com.selinuxtoolbox.core.ui.theme.WarningYellow

@Composable
fun StatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
        modifier = modifier
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SeverityChip(severity: ConflictSeverity, modifier: Modifier = Modifier) {
    val (label, color) = when (severity) {
        ConflictSeverity.CRITICAL -> "CRITICAL" to CriticalRed
        ConflictSeverity.HIGH     -> "HIGH"     to PermissiveAmber
        ConflictSeverity.MEDIUM   -> "MEDIUM"   to WarningYellow
        ConflictSeverity.LOW      -> "LOW"      to OrphanedGrey
        ConflictSeverity.INFO     -> "INFO"     to ReviewBlue
    }
    StatusChip(label = label, color = color, modifier = modifier)
}

@Composable
fun ClassificationChip(
    classification: RuleClassification,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (classification) {
        RuleClassification.ACTIVE    -> "ACTIVE"    to EnforcingGreen
        RuleClassification.ORPHANED  -> "ORPHANED"  to OrphanedGrey
        RuleClassification.WRONG_SOC -> "WRONG SoC" to CriticalRed
        RuleClassification.WRONG_ROM -> "WRONG ROM" to PermissiveAmber
        RuleClassification.PROTECTED -> "PROTECTED" to ReviewBlue
        RuleClassification.REVIEW    -> "REVIEW"    to WarningYellow
        RuleClassification.UNKNOWN   -> "UNKNOWN"   to OrphanedGrey
    }
    StatusChip(label = label, color = color, modifier = modifier)
}

@Composable
fun SeclabelStatusChip(
    status: SeclabelStatus,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (status) {
        SeclabelStatus.VALID      -> "VALID"      to SafeGreen
        SeclabelStatus.MISSING    -> "MISSING"    to CriticalRed
        SeclabelStatus.SUSPICIOUS -> "SUSPICIOUS" to PermissiveAmber
    }
    StatusChip(label = label, color = color, modifier = modifier)
}
