package com.example.foodtracker.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lățimea maximă a conținutului pe ecranele late (tablete).
 *
 * Telefoanele au în general 360–410dp lățime, deci sunt deja sub acest plafon și
 * constrângerea nu se activează — aspectul de telefon rămâne neschimbat. Pe
 * tabletă, conținutul se oprește la această lățime și se centrează, în loc să se
 * întindă pe toată lățimea ecranului. Fundalul fiecărui ecran rămâne full-bleed.
 */
val ContentMaxWidth: Dp = 480.dp
