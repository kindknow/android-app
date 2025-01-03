package one.mixin.android.ui.landing.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.mixin.android.Constants
import one.mixin.android.R
import one.mixin.android.compose.MixinTopAppBar
import one.mixin.android.compose.theme.MixinAppTheme
import one.mixin.android.extension.openUrl

@Composable
fun SetPinPage(next: () -> Unit) {
    val context = LocalContext.current
    MixinAppTheme {
        Column {
            MixinTopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = {
                        context.openUrl(Constants.HelpLink.TIP)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_support),
                            contentDescription = null,
                            tint = MixinAppTheme.colors.icon,
                        )
                    }
                },
            )
            Column(modifier = Modifier.padding(horizontal = 37.dp)) {
                Spacer(modifier = Modifier.height(20.dp))
                Icon(painter = painterResource(R.drawable.ic_set_up_pin), tint = Color.Unspecified, contentDescription = null)
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    stringResource(
                        R.string.Set_up_Pin
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    fontSize = 18.sp, fontWeight = FontWeight.W600, color = MixinAppTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                HighlightedTextWithClick(
                    stringResource(R.string.Set_up_Pin_desc, stringResource(R.string.More_Information)),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    stringResource(R.string.More_Information),
                    color = MixinAppTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                ) {
                    context.openUrl(Constants.HelpLink.TIP)
                }
                Spacer(modifier = Modifier.height(16.dp))
                NumberedText(
                    modifier = Modifier
                        .fillMaxWidth(), numberStr = "1", instructionStr = stringResource(R.string.set_up_pin_instruction_1)
                )
                Spacer(modifier = Modifier.height(16.dp))
                NumberedText(
                    modifier = Modifier
                        .fillMaxWidth(), numberStr = "2", instructionStr = stringResource(R.string.set_up_pin_instruction_2)
                )
                Spacer(modifier = Modifier.height(16.dp))
                NumberedText(
                    modifier = Modifier
                        .fillMaxWidth(), numberStr = "3", instructionStr = stringResource(R.string.set_up_pin_instruction_3),
                    color = MixinAppTheme.colors.red
                )

                Spacer(modifier = Modifier.weight(1f))
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    onClick = next,
                    colors =
                    ButtonDefaults.outlinedButtonColors(
                        backgroundColor = MixinAppTheme.colors.accent
                    ),
                    shape = RoundedCornerShape(32.dp),
                    elevation =
                    ButtonDefaults.elevation(
                        pressedElevation = 0.dp,
                        defaultElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        focusedElevation = 0.dp,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.Start),
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
