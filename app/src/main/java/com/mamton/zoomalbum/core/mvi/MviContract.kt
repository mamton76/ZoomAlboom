package com.mamton.zoomalbum.core.mvi

/**
 * Marker interface for UI state representations.
 * Each feature defines its own data class implementing this interface.
 */
interface State

/**
 * Marker interface for user actions / events flowing into a ViewModel.
 */
interface Intent

/**
 * Marker interface for one-shot side-effects (navigation, toasts, etc.).
 */
interface Effect
