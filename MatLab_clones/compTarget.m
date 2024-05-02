function [delta,rad,sqr] = compTarget(mode,u,r,qR)
%compTarget(mode,u,r,qR) Given two circles, compute next
%   Given two petals in normalized setting, compute the
%   next 'target' circle. qR is the signed square root
%   of the neighbor (may be negative or zero), r is 
%   radius of opposite petal, u is uzian for edge to 
%   neighbor. Mode tells us with "Situation" are in, as
%   layed out in the preprint. delta is the displacement
%   of the target's tangency point, rad is it radius, 
%   and sqr is the signed square root of the radius,
%   which may be negative or zero.

s3=sqrt(3);

% mode 1, Situation 1, find displacement of c_{n-1}; ignore r, qR
if mode==1
    delta=2*s3*u;
    rad=1;
    sqr=1;
    return;
end

% mode 2, Situation 2, find petal c_2; ignore r and qR
if mode==2
    delta=2/(s3*u);
    rad=1/(3*u*u);
    sqr=1/(s3*u);
    return;
end

% mode 3, Situation 3, generic
if mode==3
    delta=2*qR/(s3*u/qR-1/sqrt(r));
    sqr=1/(s3*u/qR-1/sqrt(r));
    rad=sqr^2;
    return;
end

% mode 4, Situation 4, qR is 0 or negative.
if mode==4 
    % When qR=0, target petal is tangent to
    % center at infinity (so it's a half plane with
    % bdry parallel to real axis and through -2*r*i).
    if qR==0
        delta=2*s3*u*r;
        rad=r;
        sqr=sqrt(r);
        return;
    end
    % When qR<0, previous target was left of the
    % circle for r; formula for delta is same as
    % situation 3, although qR is now negative
    delta=2*qR/(s3*u/qR-1/sqrt(r));
    rad=(1/(s3*u/qR-1/sqrt(r)))^2;
    sqr=1/(s3*u/qR-1/sqrt(r));
    return;
end

end