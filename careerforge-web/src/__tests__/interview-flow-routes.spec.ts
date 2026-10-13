import { describe, expect, it } from 'vitest'

import router from '@/router'
import { ROUTE_PATHS } from '@/router/paths'

describe('interview flow routes', () => {
  it('publishes the public, legal and not-found routes', () => {
    const names = router.getRoutes().map((route) => route.name)

    expect(names).toEqual(
      expect.arrayContaining(['home', 'login', 'register', 'terms', 'privacy', 'not-found']),
    )
    expect(ROUTE_PATHS.terms).toBe('/terms')
    expect(ROUTE_PATHS.privacy).toBe('/privacy')
  })

  it('publishes start, records, ready, session and report routes', () => {
    const names = router.getRoutes().map((route) => route.name)

    expect(names).toEqual(
      expect.arrayContaining([
        'interviews',
        'interview-records',
        'interview-ready',
        'interview-session',
        'interview-report',
      ]),
    )
    expect(names).not.toContain('interview-detail')
  })

  it.each([
    'dashboard',
    'target-role',
    'skill-gaps',
    'memories',
    'training-plans',
    'training-plan-detail',
    'coach',
    'coach-session',
    'knowledge',
    'evidence',
  ])('redirects retired route %s to the interview start page', (routeName) => {
    const route = router.getRoutes().find((candidate) => candidate.name === routeName)

    expect(route?.redirect).toBe(ROUTE_PATHS.interviews)
  })

  it('uses the immersive layout only while configuring, preparing or conducting an interview', () => {
    const immersiveRouteNames = [
      'comprehensive-interview-setup',
      'interview-ready',
      'interview-session',
    ]

    immersiveRouteNames.forEach((routeName) => {
      const route = router.getRoutes().find((candidate) => candidate.name === routeName)
      expect(route?.meta.immersive).toBe(true)
    })

    const reportRoute = router
      .getRoutes()
      .find((candidate) => candidate.name === 'interview-report')
    expect(reportRoute?.meta.immersive).not.toBe(true)
  })
})
