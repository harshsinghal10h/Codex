import math
import unittest
from intelligence.timeline import Schedule,from_samples,normalize

class TimelineTests(unittest.TestCase):
    def test_skip_example(self):
        s=Schedule([{'start':165000,'end':179500}],300000)
        self.assertIsNone(s.target(164799));self.assertEqual(s.target(164800),179500)
        self.assertEqual(s.target(170000),179500);self.assertIsNone(s.target(179500))
    def test_speed_guard(self):
        s=Schedule([{'start':10000,'end':15000}],20000)
        self.assertEqual(s.target(9200,4),15000);self.assertIsNone(s.target(9199,4))
    def test_reverse_seek(self):
        s=Schedule([{'start':1000,'end':2000},{'start':5000,'end':6000}],10000)
        self.assertEqual(s.target(5500),6000);self.assertEqual(s.target(1500),2000)
    def test_last_section(self):
        self.assertEqual(Schedule([{'start':8000,'end':10000}],10000).target(9000),10000)
    def test_sort_merge_clamp(self):
        spans=normalize([{'start':5000,'end':15000},{'start':1000,'end':4800}],10000)
        self.assertEqual([(s.start,s.end) for s in spans],[(1000,10000)])
    def test_invalid_spans(self):
        for span in [{'start':-1,'end':1},{'start':2,'end':1},{'start':0,'end':math.nan}]:
            with self.assertRaises(ValueError):normalize([span],1000)
    def test_all_clear(self):
        self.assertEqual(from_samples([(t,0) for t in range(0,3000,500)],3000),[])
    def test_first_frame(self):
        span=from_samples([(t,1 if t==0 else 0) for t in range(0,3000,500)],3000)[0]
        self.assertEqual((span.start,span.end),(0,1000))
    def test_held_low_hysteresis(self):
        spans=from_samples([(0,0),(500,.7),(1000,.4),(1500,.2),(2000,.1),(2500,0)],3000)
        self.assertEqual((spans[0].start,spans[0].end),(0,2000))
    def test_partial_coverage_rejected(self):
        for samples in [[(0,0),(1000,0)],[(0,0)],[(0,0),(500,float('nan'))]]:
            with self.assertRaises(ValueError):from_samples(samples,3000)
    def test_finish_flagged(self):
        span=from_samples([(t,.8) for t in range(0,3000,500)],3000)[0]
        self.assertEqual((span.start,span.end),(0,3000))
    def test_empty_schedule(self):
        self.assertIsNone(Schedule([],3000).target(0))
    def test_lookup_does_not_depend_on_wall_clock(self):
        s=Schedule([{'start':165000,'end':180000}],300000)
        self.assertEqual([s.target(x) for x in [164800,170000,5000,165000]],[180000,180000,None,180000])

if __name__=='__main__':unittest.main()
